package g_mungus.zps.commands.predicates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.exceptions.CancellationException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import java.util.Collections;

public class BlockStatePredicateCommand {

    public static CommandNode<CommandSourceStack> build(CommandBuildContext buildContext, CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("IF_STATE").then(
                Commands.argument("state_predicate", BlockPredicateArgument.blockPredicate(buildContext)).forward(
                        dispatcher.getRoot().getChild(ZPSCommands.PREFIX).getChild("position"), context -> {

                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = ZPSCommands.getPosition(context);
                    BlockInWorld block = new BlockInWorld(level, pos, false);
                    var predicate = BlockPredicateArgument.getBlockPredicate(context, "state_predicate");

                    if (predicate.test(block)) {
                        return Collections.singleton(context.getSource());
                    } else {
                        throw new CancellationException(Component.literal("Operation unsuccessful: blockstate predicate did not match"));
                    }
                }, false)
        ).build();
    }
}
