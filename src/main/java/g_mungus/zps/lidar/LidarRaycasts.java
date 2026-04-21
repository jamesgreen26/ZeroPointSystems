package g_mungus.zps.lidar;

import g_mungus.zps.compat.Compat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class LidarRaycasts {

    public static final List<RayCast> raycasters = new ArrayList<>();

    public static double raycast(Level level, Vec3 start, Vec3 dir, double length) {
        Compat.RayTransform transform = Compat.transformLidarRay(level, start, dir);
        Vec3 transformedStart = transform.start();
        Vec3 transformedDir = transform.dir();

        double result = Double.MAX_VALUE;

        for (var raycaster : raycasters) {
            double dist = raycaster.invoke(level, transformedStart, transformedDir, length);

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
}
