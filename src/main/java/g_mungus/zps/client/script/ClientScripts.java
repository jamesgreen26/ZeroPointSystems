package g_mungus.zps.client.script;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.codec.ScriptTreeCodec;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.sync.ZPSHostCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The scripts as the server last sent them: a view of the script tree, for the editors to parse,
 * suggest and highlight with, and the "works with" lists the ponder index shows.
 *
 * <p>Chat gets the tree too: the server's {@code /zps_script <pos> <command>} reads the command as
 * one string, and here {@code <pos>} is pointed at the script tree instead, so chat suggests and
 * colours the script as the editors do.
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID, value = Dist.CLIENT)
public final class ClientScripts {
    private static volatile @Nullable ScriptView<SharedSuggestionProvider> view;

    public static final Set<ResourceLocation> commandCapableBlocks = ConcurrentHashMap.newKeySet();
    public static final Set<ResourceLocation> getterCapableBlocks = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, List<String>> executorNamesByBlock = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, List<String>> getterNamesByBlock = new ConcurrentHashMap<>();

    private ClientScripts() {
    }

    /** The scripts the server sent, or null before any have arrived or if they could not be read. */
    public static @Nullable ScriptView<SharedSuggestionProvider> view() {
        return view;
    }

    public static void receive(byte[] tree, Map<ResourceLocation, List<String>> executors,
                               Map<ResourceLocation, List<String>> getters) {
        executorNamesByBlock.clear();
        executorNamesByBlock.putAll(executors);
        commandCapableBlocks.clear();
        commandCapableBlocks.addAll(executors.keySet());
        getterNamesByBlock.clear();
        getterNamesByBlock.putAll(getters);
        getterCapableBlocks.clear();
        getterCapableBlocks.addAll(getters.keySet());

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        CommandBuildContext buildContext = CommandBuildContext.simple(connection.registryAccess(),
                connection.enabledFeatures());
        try {
            view = new ScriptTreeCodec(new ZPSHostCodec(buildContext))
                    .decode(new DataInputStream(new ByteArrayInputStream(tree)), Host.INSTANCE, ZPSScriptTypes.all());
        } catch (IOException | RuntimeException e) {
            ZPSMod.LOGGER.error("Could not read the script tree the server sent; script editors will not suggest", e);
            view = null;
        }
        if (connection instanceof ScriptCommandsHolder holder) {
            holder.zps$graftScripts();
        }
    }

    /**
     * {@code commands} with the server's {@code /zps_script} replaced by one that reads the script
     * tree. Brigadier cannot take a node back out, so the root is rebuilt around it. Left as it is
     * when the server offered no {@code /zps_script}, as for a player who may not use it.
     */
    public static CommandDispatcher<SharedSuggestionProvider> graft(CommandDispatcher<SharedSuggestionProvider> commands) {
        ScriptView<SharedSuggestionProvider> scripts = view;
        RootCommandNode<SharedSuggestionProvider> root = commands.getRoot();
        if (scripts == null || root.getChild(ZPSCommands.Paths.SCRIPT) == null) {
            return commands;
        }
        RootCommandNode<SharedSuggestionProvider> grafted = new RootCommandNode<>();
        for (CommandNode<SharedSuggestionProvider> child : root.getChildren()) {
            if (!child.getName().equals(ZPSCommands.Paths.SCRIPT)) {
                grafted.addChild(child);
            }
        }
        grafted.addChild(LiteralArgumentBuilder.<SharedSuggestionProvider>literal(ZPSCommands.Paths.SCRIPT)
                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, Coordinates>argument(
                                ZPSCommands.Paths.POSITION, BlockPosArgument.blockPos())
                        .redirect(scripts.scriptRoot()))
                .build());
        return new CommandDispatcher<>(grafted);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        view = null;
        commandCapableBlocks.clear();
        getterCapableBlocks.clear();
        executorNamesByBlock.clear();
        getterNamesByBlock.clear();
    }

    /** What the client's command dispatcher is reached through, to graft the scripts into it. */
    public interface ScriptCommandsHolder {
        void zps$graftScripts();
    }

    /**
     * Parses and suggests for the editors and chat. A node meant for some blocks is offered where
     * the script can reach one of them; where that is not known, everything is offered.
     */
    private static final class Host implements ScriptViewHost<SharedSuggestionProvider> {
        static final Host INSTANCE = new Host();

        @Override
        public Object hostContext(SharedSuggestionProvider source) {
            return source instanceof EditorSuggestionSource editor ? editor : new EditorSuggestionSource(source, null);
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            Set<ResourceLocation> connected = context.host(EditorSuggestionSource.class).connectedBlocks();
            if (connected == null || !(applicability instanceof BlockApplicability blocks)) {
                return Match.UNRESTRICTED;
            }
            return blocks.appliesToAny(connected) ? Match.EXPLICIT : Match.NONE;
        }

        @Override
        public String defaultNamespace() {
            return ZPSMod.MOD_ID;
        }
    }
}
