package g_mungus.zps.commands.lang.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

@FunctionalInterface
public interface BlockPosProvider extends Provider<BlockPos> {
    BlockPos get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
}
