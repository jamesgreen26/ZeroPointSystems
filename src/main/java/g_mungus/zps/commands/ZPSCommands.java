package g_mungus.zps.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.actions.SetRedstoneCommand;
import g_mungus.zps.commands.actions.TurnPageCommand;
import g_mungus.zps.commands.predicates.BlockPosListPredicateCommand;
import g_mungus.zps.commands.predicates.BlockPosPredicateCommand;
import g_mungus.zps.commands.predicates.BlockStatePredicateCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
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

        zpsScript(dispatcher, SetRedstoneCommand.COMMAND);
        zpsScript(dispatcher, TurnPageCommand.COMMAND);
        zpsScript(dispatcher, BlockStatePredicateCommand.build(buildContext, dispatcher));
        zpsScript(dispatcher, BlockPosPredicateCommand.build(dispatcher));
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
}
