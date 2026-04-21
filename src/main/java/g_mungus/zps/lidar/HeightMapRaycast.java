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
    private static final double EPSILON = 1.0E-12;


    /// Returns the first ray entry into the volume formed between min build height and the motion-blocking heightmap.
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        double dirLengthSqr = dir.lengthSqr();
        if (length <= 0.0 || dirLengthSqr < 1.0E-10) {
            return -1.0;
        }

        double invDirLength = 1.0 / Math.sqrt(dirLengthSqr);
        double dirX = dir.x * invDirLength;
        double dirY = dir.y * invDirLength;
        double dirZ = dir.z * invDirLength;

        double startX = start.x;
        double startY = start.y;
        double startZ = start.z;

        int minBuildHeight = level.getMinBuildHeight();
        ChunkCache cache = new ChunkCache();

        if (isInsideHeightMapVolume(level, startX, startY, startZ, minBuildHeight, cache)) {
            return 0.0;
        }

        int stepX = dirX > EPSILON ? 1 : dirX < -EPSILON ? -1 : 0;
        int stepZ = dirZ > EPSILON ? 1 : dirZ < -EPSILON ? -1 : 0;

        // Vertical ray: only a single XZ column is visited.
        if (stepX == 0 && stepZ == 0) {
            int blockX = Mth.floor(startX);
            int blockZ = Mth.floor(startZ);
            int heightTop = getHeightTop(level, blockX, blockZ, cache);
            if (heightTop == Integer.MIN_VALUE) {
                return -1.0;
            }
            return solveEntryDistance(0.0, length, startY, dirY, minBuildHeight, heightTop);
        }

        int blockX = Mth.floor(startX);
        int blockZ = Mth.floor(startZ);

        double tDeltaX;
        double tMaxX;
        if (stepX > 0) {
            tDeltaX = 1.0 / dirX;
            tMaxX = ((blockX + 1.0) - startX) / dirX;
        } else if (stepX < 0) {
            tDeltaX = 1.0 / -dirX;
            tMaxX = (startX - blockX) / -dirX;
        } else {
            tDeltaX = Double.POSITIVE_INFINITY;
            tMaxX = Double.POSITIVE_INFINITY;
        }

        double tDeltaZ;
        double tMaxZ;
        if (stepZ > 0) {
            tDeltaZ = 1.0 / dirZ;
            tMaxZ = ((blockZ + 1.0) - startZ) / dirZ;
        } else if (stepZ < 0) {
            tDeltaZ = 1.0 / -dirZ;
            tMaxZ = (startZ - blockZ) / -dirZ;
        } else {
            tDeltaZ = Double.POSITIVE_INFINITY;
            tMaxZ = Double.POSITIVE_INFINITY;
        }

        double t = 0.0;
        while (t <= length) {
            double tExit = Math.min(length, Math.min(tMaxX, tMaxZ));

            int heightTop = getHeightTop(level, blockX, blockZ, cache);
            if (heightTop != Integer.MIN_VALUE) {
                double hitDistance = solveEntryDistance(t, tExit, startY, dirY, minBuildHeight, heightTop);
                if (hitDistance >= 0.0) {
                    return hitDistance;
                }
            }

            if (tExit >= length) {
                break;
            }

            boolean advanceX = tMaxX <= tMaxZ;
            boolean advanceZ = tMaxZ <= tMaxX;
            if (advanceX) {
                blockX += stepX;
                tMaxX += tDeltaX;
            }
            if (advanceZ) {
                blockZ += stepZ;
                tMaxZ += tDeltaZ;
            }
            t = tExit;
        }

        return -1.0;
    }

    private static boolean isInsideHeightMapVolume(Level level, double x, double y, double z, int minBuildHeight, ChunkCache cache) {
        if (y < minBuildHeight) {
            return false;
        }

        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int heightTop = getHeightTop(level, blockX, blockZ, cache);
        if (heightTop == Integer.MIN_VALUE) {
            return false;
        }

        return y < heightTop;
    }

    private static int getHeightTop(Level level, int blockX, int blockZ, ChunkCache cache) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        ChunkAccess chunk = getChunkCached(level, chunkX, chunkZ, cache);
        if (chunk == null) {
            return Integer.MIN_VALUE;
        }
        return chunk.getHeight(HEIGHTMAP_TYPE, blockX & 15, blockZ & 15) + 1;
    }

    private static double solveEntryDistance(double tEnter, double tExit, double startY, double dirY, int minBuildHeight, int heightTop) {
        if (tExit < tEnter) {
            return -1.0;
        }

        double lower = tEnter;
        boolean lowerStrict = false;
        double upper = tExit;
        boolean upperStrict = false;

        if (dirY > EPSILON) {
            double minBound = (minBuildHeight - startY) / dirY;
            if (minBound > lower) {
                lower = minBound;
                lowerStrict = false;
            }

            double topBound = (heightTop - startY) / dirY;
            if (topBound < upper) {
                upper = topBound;
                upperStrict = true;
            } else if (topBound == upper) {
                upperStrict = true;
            }
        } else if (dirY < -EPSILON) {
            double minBound = (minBuildHeight - startY) / dirY;
            if (minBound < upper) {
                upper = minBound;
                upperStrict = false;
            }

            double topBound = (heightTop - startY) / dirY;
            if (topBound > lower) {
                lower = topBound;
                lowerStrict = true;
            } else if (topBound == lower) {
                lowerStrict = true;
            }
        } else if (startY < minBuildHeight || startY >= heightTop) {
            return -1.0;
        }

        if (lower > upper) {
            return -1.0;
        }
        if (lower == upper && (lowerStrict || upperStrict)) {
            return -1.0;
        }

        double candidate = lowerStrict ? Math.nextUp(lower) : lower;
        if (upperStrict) {
            return candidate < upper ? candidate : -1.0;
        }
        return candidate <= upper ? candidate : -1.0;
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
