package g_mungus.zps.lidar;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class HeightMapRaycast implements RayCast {
    public static final HeightMapRaycast INSTANCE = new HeightMapRaycast();
    private static final Heightmap.Types HEIGHTMAP_TYPE = Heightmap.Types.MOTION_BLOCKING;


    /// Approximates a raycast and returns the distance of the first intersection of the volumes formed between
    /// the bottom of build height (underneath bedrock) and the top of the height map
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        if (length <= 0.0 || dir.lengthSqr() < 1.0E-10) {
            return -1.0;
        }

        Vec3 normalizedDir = dir.normalize();
        double stepSize = 0.25;
        int sampleCount = Mth.ceil(length / stepSize);
        int minBuildHeight = level.getMinBuildHeight();
        ChunkCache cache = new ChunkCache();

        double previousDistance = 0.0;
        if (isInsideHeightMapVolume(level, start, minBuildHeight, cache)) {
            return 0.0;
        }

        for (int i = 1; i <= sampleCount; i++) {
            double sampleDistance = Math.min(i * stepSize, length);
            Vec3 samplePoint = start.add(normalizedDir.scale(sampleDistance));
            boolean inside = isInsideHeightMapVolume(level, samplePoint, minBuildHeight, cache);

            if (inside) {
                // Refine entry point between the previous sample and this sample.
                double low = previousDistance;
                double high = sampleDistance;
                for (int j = 0; j < 10; j++) {
                    double mid = (low + high) * 0.5;
                    Vec3 midPoint = start.add(normalizedDir.scale(mid));
                    if (isInsideHeightMapVolume(level, midPoint, minBuildHeight, cache)) {
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

    private static boolean isInsideHeightMapVolume(Level level, Vec3 point, int minBuildHeight, ChunkCache cache) {
        if (point.y < minBuildHeight) {
            return false;
        }

        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ChunkAccess chunk = getChunkCached(level, chunkX, chunkZ, cache);
        if (chunk == null) {
            return false;
        }

        int heightTop = chunk.getHeight(HEIGHTMAP_TYPE, x & 15, z & 15) + 1;
        return point.y < heightTop;
    }

    private static ChunkAccess getChunkCached(Level level, int chunkX, int chunkZ, ChunkCache cache) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (cache.firstKey == chunkKey) {
            return cache.firstChunk;
        }
        if (cache.secondKey == chunkKey) {
            ChunkAccess secondChunk = cache.secondChunk;
            // Promote second entry to first to keep the hottest chunk in slot 1.
            cache.secondKey = cache.firstKey;
            cache.secondChunk = cache.firstChunk;
            cache.firstKey = chunkKey;
            cache.firstChunk = secondChunk;
            return secondChunk;
        }

        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }

        ChunkAccess loadedChunk = level.getChunk(chunkX, chunkZ);
        cache.secondKey = cache.firstKey;
        cache.secondChunk = cache.firstChunk;
        cache.firstKey = chunkKey;
        cache.firstChunk = loadedChunk;
        return loadedChunk;
    }

    private static final class ChunkCache {
        private long firstKey = Long.MIN_VALUE;
        private ChunkAccess firstChunk;
        private long secondKey = Long.MIN_VALUE;
        private ChunkAccess secondChunk;
    }
}
