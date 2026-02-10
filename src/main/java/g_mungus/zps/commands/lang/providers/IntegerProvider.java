package g_mungus.zps.commands.lang.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;

@FunctionalInterface
public interface IntegerProvider extends Provider<Integer>  {
    Integer get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
}
