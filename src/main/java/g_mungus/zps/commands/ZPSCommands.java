package g_mungus.zps.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.actions.SetRedstoneCommand;
import g_mungus.zps.commands.actions.TurnPageCommand;
import g_mungus.zps.commands.lang.arguments.BuiltinArgumentTypes;
import g_mungus.zps.commands.lang.commands.IfUnlessCommand;
import g_mungus.zps.commands.lang.comparators.BuiltinComparisons;
import g_mungus.zps.commands.predicates.BlockPosListPredicateCommand;
import g_mungus.zps.commands.predicates.BlockPosPredicateCommand;
import g_mungus.zps.commands.predicates.BlockStatePredicateCommand;
import g_mungus.zps.commands.lang.providers.BuiltinProviders;
import g_mungus.zps.commands.lang.providers.RegisterScriptArgumentProvidersEvent;
import g_mungus.zps.commands.lang.converters.BuiltinConverters;
import g_mungus.zps.commands.lang.converters.RegisterScriptArgumentProviderConvertersEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ZPSCommands {

    public static final String PREFIX = "zps_script";

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        BuiltinConverters.register();
        MinecraftForge.EVENT_BUS.post(new RegisterScriptArgumentProviderConvertersEvent());
        BuiltinProviders.register();
        MinecraftForge.EVENT_BUS.post(new RegisterScriptArgumentProvidersEvent());

        BuiltinComparisons.register();
        BuiltinArgumentTypes.register();

        zpsScript(dispatcher, SetRedstoneCommand.COMMAND);
        zpsScript(dispatcher, TurnPageCommand.COMMAND);

        zpsScript(dispatcher, IfUnlessCommand.buildForType(dispatcher, BlockPos.class, IfUnlessCommand.PredicateType.IF).build());
        zpsScript(dispatcher, IfUnlessCommand.buildForType(dispatcher, BlockPos.class, IfUnlessCommand.PredicateType.UNLESS).build());



//        zpsScript(dispatcher, BlockStatePredicateCommand.build(buildContext, dispatcher));
//        zpsScript(dispatcher, BlockPosPredicateCommand.build(dispatcher));
        zpsScript(dispatcher, BlockPosListPredicateCommand.build(dispatcher));
    }

    private static void zpsScript(CommandDispatcher<CommandSourceStack> dispatcher, CommandNode<CommandSourceStack> command) {
        dispatcher.register(
                Commands.literal(PREFIX)
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .then(command).build()));
    }

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

    public static CommandNode<CommandSourceStack> getScriptRootNode(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.getRoot().getChild(ZPSCommands.PREFIX).getChild("position");
    }

    public static <S> CommandDispatcher<S> getScriptDispatcher(CommandDispatcher<S> rootDispatcher) {
        CommandDispatcher<S> output = new CommandDispatcher<>();

        CommandNode<S> sourceParent =
                rootDispatcher.getRoot()
                        .getChild(PREFIX)
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


}
