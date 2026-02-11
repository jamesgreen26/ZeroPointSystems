package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class ZPSCommands {

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(
                Commands.literal(Paths.INTERNAL)
                        .requires(commandSourceStack -> false)
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
                                    source.setPos(BlockPosArgument.getBlockPos(context, "position"));
                                    return List.of(context.getSource().withSource(source));
                                }, false

                        ))
        );


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
        commandTreeBuilder.buildConditionalExecutors();
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
                        .getChild(Paths.SCRIPT)
                        .getChild("position");

        if (sourceParent != null) {
            for (CommandNode<S> child : sourceParent.getChildren()) {
                output.getRoot().addChild(cloneNode(child));
            }
        }

        output.register(
                LiteralArgumentBuilder.<S>literal("WAIT").then(
                RequiredArgumentBuilder.<S, Integer>argument("cycles", IntegerArgumentType.integer(1, 64))
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


    public static class Paths {
        public static final String SCRIPT = "zps_script";
        public static final String INTERNAL = "zps_script_internal";
        public static final String MAPPERS = "MAPPERS";
        public static final String GETTERS = "GETTERS";
        public static final String EXECUTORS = "EXECUTORS";
    }
}
