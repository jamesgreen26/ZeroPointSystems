package g_mungus.zps.lidar;

import g_mungus.zps.compat.Compat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class LidarRaycasts {

    public static final List<RayCast> raycasters = new ArrayList<>();
    private static final Object NO_CACHE = new Object();

    public static double raycast(Level level, Vec3 start, Vec3 dir, double length) {
        return raycast(level, start, dir, length, null);
    }

    public static double raycast(Level level, Vec3 start, Vec3 dir, double length, ScanContext scanContext) {
        Compat.RayTransform transform = Compat.transformLidarRay(level, start, dir);
        Vec3 transformedStart = transform.start();
        Vec3 transformedDir = transform.dir();
        long sourceShipId = transform.sourceShipId();

        double result = Double.MAX_VALUE;

        for (var raycaster : raycasters) {
            Object rayCache = scanContext == null ? null : scanContext.getOrCreateCache(level, raycaster);
            double dist = rayCache == null
                    ? raycaster.invoke(level, transformedStart, transformedDir, length, sourceShipId)
                    : raycaster.invokeWithCache(level, transformedStart, transformedDir, length, rayCache, sourceShipId);

            if (dist == 0.0) {
                return 0.0;
            } else if (dist > 0 && dist < result) {
                result = dist;
            }
        }
        if (result < Double.MAX_VALUE) {
            return result;
        } else {
            return -1;
        }
    }

    public static final class ScanContext {
        private final Map<RayCast, Object> rayCacheByRaycaster = new IdentityHashMap<>();

        private Object getOrCreateCache(Level level, RayCast raycaster) {
            Object cache = rayCacheByRaycaster.get(raycaster);
            if (cache != null) {
                return cache == NO_CACHE ? null : cache;
            }

            Object createdCache = raycaster.createScanCache(level);
            rayCacheByRaycaster.put(raycaster, createdCache == null ? NO_CACHE : createdCache);
            return createdCache;
        }
    }
}
