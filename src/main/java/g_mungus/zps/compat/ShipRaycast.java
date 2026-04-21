package g_mungus.zps.compat;

import g_mungus.zps.lidar.HeightMapRaycast;
import g_mungus.zps.lidar.RayCast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.world.LevelYRange;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipRaycast implements RayCast {
    public static final ShipRaycast INSTANCE = new ShipRaycast();

    private static final double EPSILON = 1.0E-9;
    private static final double MIN_DIR_LENGTH_SQR = 1.0E-10;
    private static final double QUERY_AABB_EPSILON = 1.0E-6;

    private ShipRaycast() {}

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        var sourceShip = VSGameUtilsKt.getShipManagingPos(level, start.x, start.y, start.z);
        long sourceShipId = sourceShip == null ? Compat.NO_SOURCE_SHIP_ID : sourceShip.getId();
        return invoke(level, start, dir, length, sourceShipId, new ScanCache(level));
    }

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length, long sourceShipId) {
        return invoke(level, start, dir, length, sourceShipId, new ScanCache(level));
    }

    @Override
    public Object createScanCache(Level level) {
        return new ScanCache(level);
    }

    @Override
    public double invokeWithCache(Level level, Vec3 start, Vec3 dir, double length, Object scanCache, long sourceShipId) {
        if (scanCache instanceof ScanCache typedCache) {
            return invoke(level, start, dir, length, sourceShipId, typedCache);
        }
        return invoke(level, start, dir, length, sourceShipId);
    }

    private double invoke(Level level, Vec3 start, Vec3 dir, double length, long sourceShipId, ScanCache scanCache) {
        if (length <= 0.0 || dir.lengthSqr() < MIN_DIR_LENGTH_SQR) {
            return -1.0;
        }

        Vec3 normalizedDir = dir.normalize();
        Vec3 rayEnd = start.add(normalizedDir.scale(length));
        AABB queryAabb = new AABB(
                Math.min(start.x, rayEnd.x),
                Math.min(start.y, rayEnd.y),
                Math.min(start.z, rayEnd.z),
                Math.max(start.x, rayEnd.x),
                Math.max(start.y, rayEnd.y),
                Math.max(start.z, rayEnd.z)
        ).inflate(QUERY_AABB_EPSILON);

        double bestDistance = Double.MAX_VALUE;
        for (var ship : VSGameUtilsKt.getShipsIntersecting(level, queryAabb)) {
            if (ship.getId() == sourceShipId) {
                continue;
            }

            RayInterval worldAabbInterval = rayAabbIntersection(start, normalizedDir, length, ship.getWorldAABB());
            if (worldAabbInterval == null || worldAabbInterval.enter() >= bestDistance) {
                continue;
            }

            Vector3d localStartVector = ship.getWorldToShip().transformPosition(new Vector3d(start.x, start.y, start.z));
            Vector3d localDirVector = ship.getWorldToShip().transformDirection(new Vector3d(normalizedDir.x, normalizedDir.y, normalizedDir.z));
            double localDirScale = localDirVector.length();
            if (localDirScale < EPSILON) {
                continue;
            }

            Vec3 localStart = new Vec3(localStartVector.x, localStartVector.y, localStartVector.z);
            Vec3 localDir = new Vec3(
                    localDirVector.x / localDirScale,
                    localDirVector.y / localDirScale,
                    localDirVector.z / localDirScale
            );
            double localMaxLength = length * localDirScale;

            AABBi shipyardAabb = ship.getChunkClaim().getTotalVoxelRegion(scanCache.levelYRange, scanCache.mutableShipyardAabb);
            RayInterval shipyardInterval = rayAabbIntersection(
                    localStart,
                    localDir,
                    localMaxLength,
                    shipyardAabb.minX(),
                    shipyardAabb.minY(),
                    shipyardAabb.minZ(),
                    shipyardAabb.maxX() + 1.0,
                    shipyardAabb.maxY() + 1.0,
                    shipyardAabb.maxZ() + 1.0
            );
            if (shipyardInterval == null) {
                continue;
            }

            double localEnter = Math.max(shipyardInterval.enter(), worldAabbInterval.enter() * localDirScale);
            double localExit = Math.min(shipyardInterval.exit(), worldAabbInterval.exit() * localDirScale);
            if (bestDistance < Double.MAX_VALUE) {
                localExit = Math.min(localExit, bestDistance * localDirScale);
            }
            if (localExit < localEnter) {
                continue;
            }

            Vec3 localPassStart = localStart.add(localDir.scale(localEnter));
            double localPassLength = localExit - localEnter;
            double localHit = HeightMapRaycast.INSTANCE.invokeWithCache(
                    level,
                    localPassStart,
                    localDir,
                    localPassLength,
                    scanCache.heightMapScanCache
            );
            if (localHit < 0.0) {
                continue;
            }

            double hitDistance = (localEnter + localHit) / localDirScale;
            if (hitDistance >= 0.0 && hitDistance < bestDistance) {
                bestDistance = hitDistance;
            }
        }

        return bestDistance < Double.MAX_VALUE ? bestDistance : -1.0;
    }

    private static @Nullable RayInterval rayAabbIntersection(Vec3 start, Vec3 normalizedDir, double maxLength, AABBdc aabb) {
        return rayAabbIntersection(
                start,
                normalizedDir,
                maxLength,
                aabb.minX(),
                aabb.minY(),
                aabb.minZ(),
                aabb.maxX(),
                aabb.maxY(),
                aabb.maxZ()
        );
    }

    private static @Nullable RayInterval rayAabbIntersection(
            Vec3 start,
            Vec3 normalizedDir,
            double maxLength,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        double enter = 0.0;
        double exit = maxLength;

        RayInterval xInterval = intersectAxis(start.x, normalizedDir.x, minX, maxX, enter, exit);
        if (xInterval == null) {
            return null;
        }
        enter = xInterval.enter();
        exit = xInterval.exit();

        RayInterval yInterval = intersectAxis(start.y, normalizedDir.y, minY, maxY, enter, exit);
        if (yInterval == null) {
            return null;
        }
        enter = yInterval.enter();
        exit = yInterval.exit();

        RayInterval zInterval = intersectAxis(start.z, normalizedDir.z, minZ, maxZ, enter, exit);
        if (zInterval == null) {
            return null;
        }

        if (zInterval.exit() < zInterval.enter()) {
            return null;
        }
        return zInterval;
    }

    private static @Nullable RayInterval intersectAxis(double origin, double direction, double min, double max, double currentEnter, double currentExit) {
        if (Math.abs(direction) < EPSILON) {
            if (origin < min || origin > max) {
                return null;
            }
            return new RayInterval(currentEnter, currentExit);
        }

        double t0 = (min - origin) / direction;
        double t1 = (max - origin) / direction;
        if (t0 > t1) {
            double tmp = t0;
            t0 = t1;
            t1 = tmp;
        }

        double enter = Math.max(currentEnter, t0);
        double exit = Math.min(currentExit, t1);
        if (exit < enter) {
            return null;
        }
        return new RayInterval(enter, exit);
    }

    private record RayInterval(double enter, double exit) {}

    private static final class ScanCache {
        private final Object heightMapScanCache;
        private final LevelYRange levelYRange;
        private final AABBi mutableShipyardAabb = new AABBi();

        private ScanCache(Level level) {
            this.heightMapScanCache = HeightMapRaycast.INSTANCE.createScanCache(level);
            this.levelYRange = new LevelYRange(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        }
    }
}
