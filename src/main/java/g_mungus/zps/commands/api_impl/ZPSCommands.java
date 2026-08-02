package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptNode;
import g_mungus.zps.commands.api_impl.debug.BrigadierCanvasExporter;
import g_mungus.zps.commands.debug.PlaceBlockPanoramaCommand;
import g_mungus.zps.commands.debug.SetHeldItemEnergyCommand;
import g_mungus.zps.networking.ExecutorBlocksS2CPacket;
import g_mungus.zps.networking.GetterBlocksS2CPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mod.EventBusSubscriber
public class ZPSCommands {

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(
                Commands.literal(Paths.INTERNAL)
                        .then(Commands.literal(Paths.EXECUTORS))
                        .then(Commands.literal(Paths.MAPPERS))
                        .then(Commands.literal(Paths.GETTERS))

        );

        dispatcher.register(
                Commands.literal(Paths.SCRIPT)
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("position", BlockPosArgument.blockPos()).forward(dispatcher.getRoot()
                                        .getChild(Paths.INTERNAL)
                                        .getChild(Paths.EXECUTORS),
                                context -> {
                                    ZPSScriptCommandSource source = new ZPSScriptCommandSource(context.getSource().source);
                                    source.setCommandInput(context.getInput());
                                    source.setPos(BlockPosArgument.getBlockPos(context, "position"));
                                    return List.of(context.getSource().withSource(source));
                                }, false

                        ))
        );


        // RegisterCommandsEvent fires again on /reload; without clearing, structurally
        // identical nodes that lack value-based equality (ScriptMapper2's wrapper lambda,
        // executors using argument types without equals) accumulate as duplicates and
        // produce ambiguous duplicate branches in the rebuilt tree.
        Registry.clear();

        MinecraftForge.EVENT_BUS.post(new RegisterScriptCommandsEvent() {
            @Override
            public void register(ScriptNode node) {
                Registry.register(node);
            }

            @Override
            public CommandBuildContext buildContext() {
                return buildContext;
            }
        });


        CommandTreeBuilder commandTreeBuilder = new CommandTreeBuilder(dispatcher);


        commandTreeBuilder.buildMappers();
        commandTreeBuilder.buildGetters();
        commandTreeBuilder.buildValueOfDispatchers();
        commandTreeBuilder.buildConditionalExecutors();
        commandTreeBuilder.buildExecutors();

        if (!FMLLoader.isProduction()) {
            dispatcher.register(PlaceBlockPanoramaCommand.COMMAND);
            dispatcher.register(SetHeldItemEnergyCommand.COMMAND);

            try {
                String output = new BrigadierCanvasExporter<CommandSourceStack>().export(dispatcher.getRoot().getChild(Paths.INTERNAL));
                Files.writeString(Path.of("commands.canvas"), output);
            } catch (Exception e) {
                ZPSMod.LOGGER.warn("Command tree export failed", e);
            }
        }
    }



    @Deprecated(forRemoval = true)
    ///  use this to ensure that chained commands work properly
    public static BlockPos getPosition(CommandContext<CommandSourceStack> context) {
        try {
            return BlockPosArgument.getBlockPos(context, "position");
        } catch (Exception e) {
            try {
                String input = context.getInput();
                if (input.startsWith("/")) input = input.substring(1);
                input = input.replace("zps_script ", "");

                return BlockPosArgument.blockPos().parse(new StringReader(input)).getBlockPos(context.getSource());
            } catch (CommandSyntaxException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static <S> CommandDispatcher<S> getScriptDispatcher(CommandDispatcher<S> rootDispatcher) {
        CommandDispatcher<S> output = new CommandDispatcher<>();

        CommandNode<S> sourceParent =
                rootDispatcher.getRoot()
                        .getChild(Paths.INTERNAL)
                        .getChild(Paths.EXECUTORS);

        if (sourceParent != null) {
            for (CommandNode<S> child : sourceParent.getChildren()) {
                output.getRoot().addChild(cloneNode(child));
            }
        }

        output.register(
                LiteralArgumentBuilder.<S>literal("wait").then(
                RequiredArgumentBuilder.<S, Integer>argument("int", IntegerArgumentType.integer(1, 64))
                        .executes((a) -> 1)));

        return output;
    }

    private static <S> CommandNode<S> cloneNode(CommandNode<S> original) {
        CommandNode<S> copy = original.createBuilder().build();

        for (CommandNode<S> child : original.getChildren()) {
            copy.addChild(cloneNode(child));
        }

        return copy;
    }

    @Nullable
    public static ScriptExecutor<?, ?> getExecutor(String key) {
        for (var executor : Registry.EXECUTORS) {
            if (executor.displayName().equals(key)) {
                return executor;
            }
        }
        return null;
    }

    @Nullable
    public static ScriptGetter<?> getGetter(String key) {
        for (var getter : Registry.GETTERS) {
            if (getter.displayName().equals(key)) {
                return getter;
            }
        }
        return null;
    }

    @Nullable
    public static ScriptMapper<?, ?> getMapper(String key) {
        for (var mapper : Registry.MAPPERS) {
            if (mapper.displayName().equals(key)) {
                return mapper;
            }
        }
        return null;
    }

    public static boolean isGetterName(String key) {
        return getGetter(key) != null;
    }

    public static boolean isMapperName(String key) {
        return getMapper(key) != null;
    }

    public static Set<String> getterNames() {
        return Registry.GETTERS.stream()
                .map(ScriptGetter::displayName)
                .collect(java.util.stream.Collectors.toSet());
    }


    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Set<ResourceLocation> blocksWithItems = ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item instanceof BlockItem)
                .map(item -> ((BlockItem) item).getBlock())
                .map(ForgeRegistries.BLOCKS::getKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<ResourceLocation> allAssociatedBlocks = Registry.EXECUTORS.stream()
                .filter(e -> e.associatedBlocks() != null)
                .flatMap(e -> e.associatedBlocks().stream())
                .filter(blocksWithItems::contains)
                .toList();

        ZPSGamePackets.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ExecutorBlocksS2CPacket(allAssociatedBlocks)
        );

        List<ResourceLocation> allAssociatedGetterBlocks = Registry.GETTERS.stream()
                .filter(g -> g.associatedBlocks() != null)
                .flatMap(g -> g.associatedBlocks().stream())
                .filter(blocksWithItems::contains)
                .toList();

        ZPSGamePackets.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GetterBlocksS2CPacket(allAssociatedGetterBlocks)
        );
    }


    public static class Paths {
        public static final String SCRIPT = "zps_script";
        public static final String INTERNAL = "zps_hide";
        public static final String MAPPERS = "MAPPERS";
        public static final String GETTERS = "GETTERS";
        public static final String EXECUTORS = "EXECUTORS";
    }
}
