package g_mungus.zps.commands.predicates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.arguments.BlockPosListArgument;
import g_mungus.zps.commands.exceptions.CancellationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public class BlockPosListPredicateCommand {

    public static CommandNode<CommandSourceStack> build(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("IF_CONTAINS_POS").then(
                Commands.argument("position_list_predicate", BlockPosListArgument.blockPosList()).forward(
                        dispatcher.getRoot().getChild(ZPSCommands.PREFIX).getChild("position"), context -> {

                    BlockPos pos = ZPSCommands.getPosition(context);

                    List<BlockPos> predicate = BlockPosListArgument.getBlockPosList(context, "position_list_predicate");

                    if (predicate.contains(pos)) {
                        return Collections.singleton(context.getSource());
                    } else {
                        throw new CancellationException(Component.literal("Operation unsuccessful: position list predicate did not match"));
                    }
                }, false)
        ).build();
    }
}
