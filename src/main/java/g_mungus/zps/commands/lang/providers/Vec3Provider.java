package g_mungus.zps.commands.lang.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface Vec3Provider extends Provider<Vec3> {
    Vec3 get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
}
