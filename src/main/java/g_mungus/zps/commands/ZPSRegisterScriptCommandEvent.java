package g_mungus.zps.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

public class ZPSRegisterScriptCommandEvent extends Event {
    private final ArgumentBuilder<CommandSourceStack, ?> builder;

    ZPSRegisterScriptCommandEvent(ArgumentBuilder<CommandSourceStack, ?> builder) {
        this.builder = builder;
    }

    public void addCommand(CommandNode<CommandSourceStack> argument) {
        builder.then(argument);
    }

    public void addCommand(ArgumentBuilder<CommandSourceStack, ?> argument) {
        addCommand(argument.build());
    }

    public List<BlockInWorld> getBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<BlockPos> coordinates = BlockPosListArgument.getBlockPosList(context, "positions");
        return coordinates.stream()
                .map(it -> new BlockInWorld(context.getSource().getLevel(), it, false))
                .filter(BlockPredicateArgument.getBlockPredicate(context, "filter")).toList();
    }
}
