package g_mungus.zps.commands.lang.converters;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class BuiltinConverters {
    public static void register() {
        ConverterRegistry.register(
                Vec3.class,
                Double.class,
                "X",
                Vec3::x
        );

        ConverterRegistry.register(
                Vec3.class,
                Double.class,
                "Y",
                Vec3::y
        );

        ConverterRegistry.register(
                Vec3.class,
                Double.class,
                "Z",
                Vec3::z
        );

        ConverterRegistry.register(
                Vec3.class,
                Double.class,
                "LENGTH",
                Vec3::length
        );

        ConverterRegistry.register(
                Vec3.class,
                Double.class,
                "VOLUME",
                v -> v.x * v.y * v.z
        );

        ConverterRegistry.register(
                BlockPos.class,
                Integer.class,
                "X",
                BlockPos::getX
        );

        ConverterRegistry.register(
                BlockPos.class,
                Integer.class,
                "Y",
                BlockPos::getY
        );

        ConverterRegistry.register(
                BlockPos.class,
                Integer.class,
                "Z",
                BlockPos::getZ
        );

    }
}
