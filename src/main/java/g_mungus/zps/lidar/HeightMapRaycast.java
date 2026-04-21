package g_mungus.zps.lidar;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class HeightMapRaycast implements RayCast {
    public static final HeightMapRaycast INSTANCE = new HeightMapRaycast();


    /// Approximates a raycast and returns the distance of the first intersection of the volumes formed between
    /// the bottom of build height (underneath bedrock) and the top of the height map
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        if (length <= 0.0 || dir.lengthSqr() < 1.0E-10) {
            return -1.0;
        }

        Vec3 normalizedDir = dir.normalize();
        double stepSize = 0.25;
        int sampleCount = Mth.ceil(length / stepSize);

        double previousDistance = 0.0;
        if (isInsideHeightMapVolume(level, start)) {
            return 0.0;
        }

        for (int i = 1; i <= sampleCount; i++) {
            double sampleDistance = Math.min(i * stepSize, length);
            Vec3 samplePoint = start.add(normalizedDir.scale(sampleDistance));
            boolean inside = isInsideHeightMapVolume(level, samplePoint);

            if (inside) {
                // Refine entry point between the previous sample and this sample.
                double low = previousDistance;
                double high = sampleDistance;
                for (int j = 0; j < 10; j++) {
                    double mid = (low + high) * 0.5;
                    Vec3 midPoint = start.add(normalizedDir.scale(mid));
                    if (isInsideHeightMapVolume(level, midPoint)) {
                        high = mid;
                    } else {
                        low = mid;
                    }
                }
                return high;
            }

            previousDistance = sampleDistance;
        }

        return -1.0;
    }

    private static boolean isInsideHeightMapVolume(Level level, Vec3 point) {
        int minBuildHeight = level.getMinBuildHeight();
        if (point.y < minBuildHeight) {
            return false;
        }

        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        int heightTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return point.y < heightTop;
    }
}
