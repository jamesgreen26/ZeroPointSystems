package g_mungus.zps.compat.genesis;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.lidar.RayCast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import shipwrights.genesis.GenesisMod;
import shipwrights.genesis.space.SpaceLevel;

public class CelestialRaycast implements RayCast {
    public static final CelestialRaycast INSTANCE = new CelestialRaycast();
    private static final double MIN_DIR_LENGTH_SQR = 1.0E-10;

    private CelestialRaycast() {}

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        return invoke(level, start, dir, length, Compat.NO_SOURCE_SHIP_ID);
    }

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length, long sourceShipId) {
        if (length <= 0.0 || dir.lengthSqr() < MIN_DIR_LENGTH_SQR) {
            return -1.0;
        }
        if (!GenesisMod.isSpaceDimension(level) && !GenesisMod.isSubSpaceDimension(level)) {
            return -1.0;
        }

        var result = SpaceLevel.celestialRaycast(
                GenesisMod.getCelestialRegistry(level),
                GenesisMod.getTicks(level),
                0f,
                new Vector3d(start.x, start.y, start.z),
                new Vector3d(dir.x, dir.y, dir.z).normalize(),
                type -> true
        );

        if (result == null) {
            return -1.0;
        }

        double hitDistanceSquared = result.getSecond();
        if (!Double.isFinite(hitDistanceSquared) || hitDistanceSquared < 0.0) {
            return -1.0;
        }

        double hitDistance = Math.sqrt(hitDistanceSquared);
        return hitDistance <= length ? hitDistance : -1.0;
    }
}
