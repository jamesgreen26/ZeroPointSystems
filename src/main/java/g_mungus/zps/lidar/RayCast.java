package g_mungus.zps.lidar;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface RayCast {
    double invoke(Level level, Vec3 start, Vec3 dir, double length);

    default double invoke(Level level, Vec3 start, Vec3 dir, double length, long sourceShipId) {
        return invoke(level, start, dir, length);
    }

    default Object createScanCache(Level level) {
        return null;
    }

    default double invokeWithCache(Level level, Vec3 start, Vec3 dir, double length, Object scanCache) {
        return invoke(level, start, dir, length);
    }

    default double invokeWithCache(Level level, Vec3 start, Vec3 dir, double length, Object scanCache, long sourceShipId) {
        return invokeWithCache(level, start, dir, length, scanCache);
    }
}
