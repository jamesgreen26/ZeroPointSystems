package g_mungus.zps.commands.predicates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.exceptions.CancellationException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import java.util.Collections;

public class BlockPosPredicateCommand {

    public static CommandNode<CommandSourceStack> build(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("IF").then(Commands.literal("POS").then(
                Commands.argument("position_predicate", BlockPosArgument.blockPos()).forward(
                        ZPSCommands.getScriptRootNode(dispatcher), context -> {

                    BlockPos pos = ZPSCommands.getPosition(context);

                    BlockPos predicate = BlockPosArgument.getBlockPos(context, "position_predicate");

                    if (pos.equals(predicate)) {
                        return Collections.singleton(context.getSource());
                    } else {
                        throw new CancellationException(Component.literal("Operation unsuccessful: position predicate did not match"));
                    }
                }, false)
        )).build();
    }
}
