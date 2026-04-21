package g_mungus.zps.compat;

import g_mungus.zps.lidar.RayCast;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipRaycast implements RayCast {
    public static final ShipRaycast INSTANCE = new ShipRaycast();

    private static final double EPSILON = 1.0E-9;
    private static final double QUERY_AABB_EPSILON = 1.0E-6;

    private ShipRaycast() {}

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        var sourceShip = VSGameUtilsKt.getShipManagingPos(level, start.x, start.y, start.z);
        long sourceShipId = sourceShip == null ? Compat.NO_SOURCE_SHIP_ID : sourceShip.getId();
        return invoke(level, start, dir, length, sourceShipId);
    }

    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length, long sourceShipId) {
        if (length <= 0.0 || dir.lengthSqr() < 1.0E-10) {
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

            RayInterval interval = rayAabbIntersection(start, normalizedDir, length, ship.getWorldAABB());
            if (interval == null || interval.enter() >= bestDistance) {
                continue;
            }

            Vec3 passStart = start.add(normalizedDir.scale(interval.enter()));
            Vec3 passEnd = start.add(normalizedDir.scale(interval.exit()));
            BlockHitResult hitResult = level.clip(
                    new ClipContext(passStart, passEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)
            );

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                double hitDistance = start.distanceTo(hitResult.getLocation());
                if (hitDistance >= 0.0 && hitDistance < bestDistance) {
                    bestDistance = hitDistance;
                }
            }
        }

        return bestDistance < Double.MAX_VALUE ? bestDistance : -1.0;
    }

    private static @Nullable RayInterval rayAabbIntersection(Vec3 start, Vec3 normalizedDir, double maxLength, AABBdc aabb) {
        double enter = 0.0;
        double exit = maxLength;

        RayInterval xInterval = intersectAxis(start.x, normalizedDir.x, aabb.minX(), aabb.maxX(), enter, exit);
        if (xInterval == null) {
            return null;
        }
        enter = xInterval.enter();
        exit = xInterval.exit();

        RayInterval yInterval = intersectAxis(start.y, normalizedDir.y, aabb.minY(), aabb.maxY(), enter, exit);
        if (yInterval == null) {
            return null;
        }
        enter = yInterval.enter();
        exit = yInterval.exit();

        RayInterval zInterval = intersectAxis(start.z, normalizedDir.z, aabb.minZ(), aabb.maxZ(), enter, exit);
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
}
