package g_mungus.zps.commands.lang.converters;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class BuiltinConverters {
    public static void register() {
        ProviderConverters.register(
                Vec3.class,
                Double.class,
                "X",
                Vec3::x
        );

        ProviderConverters.register(
                Vec3.class,
                Double.class,
                "Y",
                Vec3::y
        );

        ProviderConverters.register(
                Vec3.class,
                Double.class,
                "Z",
                Vec3::z
        );

        ProviderConverters.register(
                Vec3.class,
                Double.class,
                "LENGTH",
                Vec3::length
        );

        ProviderConverters.register(
                Vec3.class,
                Double.class,
                "VOLUME",
                v -> v.x * v.y * v.z
        );

        ProviderConverters.register(
                BlockPos.class,
                Integer.class,
                "X",
                BlockPos::getX
        );

        ProviderConverters.register(
                BlockPos.class,
                Integer.class,
                "Y",
                BlockPos::getY
        );

        ProviderConverters.register(
                BlockPos.class,
                Integer.class,
                "Z",
                BlockPos::getZ
        );

    }
}
