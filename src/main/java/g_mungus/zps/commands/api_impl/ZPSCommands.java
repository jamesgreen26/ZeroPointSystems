package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api_impl.debug.BrigadierCanvasExporter;
import g_mungus.zps.commands.debug.PlaceBlockPanoramaCommand;
import g_mungus.zps.commands.debug.ReactorDebugCommand;
import g_mungus.zps.commands.debug.SetHeldItemEnergyCommand;
import g_mungus.zps.networking.ScriptTreeS2CPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the scripts whenever the server builds its commands, offers them in chat as
 * {@code /zps_script <pos> <command>}, and sends them to clients so their editors can read them.
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class ZPSCommands {

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        ZPSScripts scripts = ZPSScripts.build(event.getBuildContext());

        // The script itself is one string here: a client grafts the script tree under this from what
        // it was sent, so chat still suggests and colours the script, and the server reads it with
        // the scripts' own dispatcher.
        dispatcher.register(Commands.literal(Paths.SCRIPT)
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument(Paths.POSITION, BlockPosArgument.blockPos())
                        .then(Commands.argument(Paths.COMMAND, StringArgumentType.greedyString())
                                .executes(ZPSCommands::runScriptCommand))));

        if (!FMLLoader.isProduction()) {
            dispatcher.register(PlaceBlockPanoramaCommand.COMMAND);
            dispatcher.register(SetHeldItemEnergyCommand.COMMAND);
            dispatcher.register(ReactorDebugCommand.COMMAND);

            try {
                String output = new BrigadierCanvasExporter<CommandSourceStack>().export(scripts.tree());
                Files.writeString(Path.of("commands.canvas"), output);
            } catch (Exception e) {
                ZPSMod.LOGGER.warn("Command tree export failed", e);
            }
        }
    }

    private static int runScriptCommand(CommandContext<CommandSourceStack> context) {
        try {
            BlockPos target = BlockPosArgument.getBlockPos(context, Paths.POSITION);
            String command = StringArgumentType.getString(context, Paths.COMMAND);
            ScriptCommandFailure failure = ZPSScripts.get().tryRun(command, context.getSource(), target);
            if (failure == null) {
                return 1;
            }
            context.getSource().sendFailure(Component.literal(failure.reason()));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    /** Now there are levels, for compat that finds its commands in one. Nobody has joined yet to be sent them. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ZPSScripts.rebuild();
    }

    /** On joining, and to everyone after a reload: the scripts as they now stand. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ZPSScripts scripts = ZPSScripts.getIfBuilt();
        if (scripts == null) {
            return;
        }
        ScriptTreeS2CPacket packet = new ScriptTreeS2CPacket(scripts.encodedTree(),
                namesByBlock(scripts, ScriptExecutor.class), namesByBlock(scripts, ScriptGetter.class));
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }

    /** The nodes of one kind each block is for, among blocks that have an item to show them by. */
    private static Map<ResourceLocation, List<String>> namesByBlock(ZPSScripts scripts, Class<? extends ScriptNode> kind) {
        Set<ResourceLocation> blocksWithItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem)
                .map(item -> BuiltInRegistries.BLOCK.getKey(((BlockItem) item).getBlock()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<ResourceLocation, List<String>> names = new HashMap<>();
        for (ScriptNode node : scripts.engine().nodes()) {
            if (!kind.isInstance(node) || !(node.applicability() instanceof BlockApplicability blocks)) {
                continue;
            }
            for (ResourceLocation block : blocks.resolve()) {
                if (blocksWithItems.contains(block)) {
                    names.computeIfAbsent(block, ignored -> new ArrayList<>()).add(node.displayName());
                }
            }
        }
        names.replaceAll((block, list) -> list.stream().distinct().sorted().toList());
        return names;
    }

    public static class Paths {
        public static final String SCRIPT = "zps_script";
        public static final String POSITION = "position";
        public static final String COMMAND = "command";
    }
}
