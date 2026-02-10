package g_mungus.zps.commands.lang.arguments;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;

public class BuiltinArgumentTypes {
    public static void register() {
        ArgumentTypeRegistry.register(Integer.class, IntegerArgumentType.integer());
        ArgumentTypeRegistry.register(Double.class, DoubleArgumentType.doubleArg());
        ArgumentTypeRegistry.registerMapped(BlockPos.class, Coordinates.class, BlockPosArgument.blockPos(), Coordinates::getBlockPos);
    }
}
