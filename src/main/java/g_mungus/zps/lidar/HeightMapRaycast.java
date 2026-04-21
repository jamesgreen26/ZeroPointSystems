package g_mungus.zps.lidar;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class HeightMapRaycast implements RayCast {
    public static final HeightMapRaycast INSTANCE = new HeightMapRaycast();
    private static final Heightmap.Types HEIGHTMAP_TYPE = Heightmap.Types.MOTION_BLOCKING;
    private static final double EPSILON = 1.0E-12;
    private static final int HEIGHT_CHUNK_MISSING = Integer.MIN_VALUE + 1;


    /// Returns the first ray entry into the volume formed between min build height and the motion-blocking heightmap.
    @Override
    public double invoke(Level level, Vec3 start, Vec3 dir, double length) {
        return invoke(level, start, dir, length, new ScanCache(level.getMinBuildHeight(), level.getMaxBuildHeight()));
    }

    @Override
    public Object createScanCache(Level level) {
        return new ScanCache(level.getMinBuildHeight(), level.getMaxBuildHeight());
    }

    @Override
    public double invokeWithCache(Level level, Vec3 start, Vec3 dir, double length, Object scanCache) {
        if (scanCache instanceof ScanCache typedCache) {
            return invoke(level, start, dir, length, typedCache);
        }
        return invoke(level, start, dir, length);
    }

    private double invoke(Level level, Vec3 start, Vec3 dir, double length, ScanCache cache) {
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

        int minBuildHeight = cache.minBuildHeight;
        int maxBuildHeight = cache.maxBuildHeight;

        if (isInsideHeightMapVolume(level, startX, startY, startZ, cache)) {
            return 0.0;
        }

        int stepX = dirX > EPSILON ? 1 : dirX < -EPSILON ? -1 : 0;
        int stepZ = dirZ > EPSILON ? 1 : dirZ < -EPSILON ? -1 : 0;

        // Vertical ray: only a single XZ column is visited.
        if (stepX == 0 && stepZ == 0) {
            int blockX = Mth.floor(startX);
            int blockZ = Mth.floor(startZ);
            int heightTop = getHeightTop(level, blockX, blockZ, cache);
            if (heightTop == HEIGHT_CHUNK_MISSING) {
                return -1.0;
            }
            double heightMapHit = solveEntryDistance(0.0, length, startY, dirY, minBuildHeight, heightTop);
            if (heightMapHit < 0.0) {
                return -1.0;
            }
            return findColumnBlockStateHit(level, blockX, blockZ, 0.0, length, startY, dirY, minBuildHeight, maxBuildHeight, heightTop, cache);
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
            if (heightTop != HEIGHT_CHUNK_MISSING) {
                double hitDistance = solveEntryDistance(t, tExit, startY, dirY, minBuildHeight, heightTop);
                if (hitDistance >= 0.0) {
                    double blockStateHit = findColumnBlockStateHit(level, blockX, blockZ, t, tExit, startY, dirY, minBuildHeight, maxBuildHeight, heightTop, cache);
                    if (blockStateHit >= 0.0) {
                        return blockStateHit;
                    }
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

    private static boolean isInsideHeightMapVolume(Level level, double x, double y, double z, ScanCache cache) {
        if (y < cache.minBuildHeight) {
            return false;
        }

        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int heightTop = getHeightTop(level, blockX, blockZ, cache);
        if (heightTop == HEIGHT_CHUNK_MISSING) {
            return false;
        }

        return y < heightTop;
    }

    private static int getHeightTop(Level level, int blockX, int blockZ, ScanCache cache) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        CachedChunk cachedChunk = getChunkCached(level, chunkX, chunkZ, cache);
        if (cachedChunk == null) {
            return HEIGHT_CHUNK_MISSING;
        }

        int localX = blockX & 15;
        int localZ = blockZ & 15;
        int index = (localZ << 4) | localX;
        return cachedChunk.heights[index];
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

    private static double findColumnBlockStateHit(
            Level level,
            int blockX,
            int blockZ,
            double tEnter,
            double tExit,
            double startY,
            double dirY,
            int minBuildHeight,
            int maxBuildHeight,
            int heightTop,
            ScanCache cache
    ) {
        if (tExit < tEnter) {
            return -1.0;
        }

        CachedChunk cachedChunk = getChunkCached(level, blockX >> 4, blockZ >> 4, cache);
        if (cachedChunk == null) {
            return -1.0;
        }

        int clampedTop = Math.min(heightTop, maxBuildHeight);
        if (clampedTop <= minBuildHeight) {
            return -1.0;
        }

        double lower = tEnter;
        double upper = tExit;

        if (dirY > EPSILON) {
            double minBound = (minBuildHeight - startY) / dirY;
            if (minBound > lower) {
                lower = minBound;
            }

            double topBound = (clampedTop - startY) / dirY;
            if (topBound < upper) {
                upper = topBound;
            }
        } else if (dirY < -EPSILON) {
            double minBound = (minBuildHeight - startY) / dirY;
            if (minBound < upper) {
                upper = minBound;
            }

            double topBound = (clampedTop - startY) / dirY;
            if (topBound > lower) {
                lower = topBound;
            }
        } else {
            if (startY < minBuildHeight || startY >= clampedTop) {
                return -1.0;
            }
        }

        if (lower > upper) {
            return -1.0;
        }

        if (dirY > EPSILON) {
            double startSampleT = lower < upper ? Math.nextUp(lower) : lower;
            double endSampleT = lower < upper ? Math.nextDown(upper) : upper;
            if (endSampleT < lower) {
                endSampleT = lower;
            }

            int yStart = Mth.floor(startY + dirY * startSampleT);
            int yEnd = Mth.floor(startY + dirY * endSampleT);
            yStart = Mth.clamp(yStart, minBuildHeight, clampedTop - 1);
            yEnd = Mth.clamp(yEnd, minBuildHeight, clampedTop - 1);

            for (int blockY = yStart; blockY <= yEnd; blockY++) {
                if (isCollidableBlock(cachedChunk, blockX, blockY, blockZ, cache.mutablePos)) {
                    double blockEntry = Math.max(lower, (blockY - startY) / dirY);
                    return blockEntry <= upper ? blockEntry : -1.0;
                }
            }
            return -1.0;
        }

        if (dirY < -EPSILON) {
            double startSampleT = lower < upper ? Math.nextUp(lower) : lower;
            double endSampleT = lower < upper ? Math.nextDown(upper) : upper;
            if (endSampleT < lower) {
                endSampleT = lower;
            }

            int yStart = Mth.floor(startY + dirY * startSampleT);
            int yEnd = Mth.floor(startY + dirY * endSampleT);
            yStart = Mth.clamp(yStart, minBuildHeight, clampedTop - 1);
            yEnd = Mth.clamp(yEnd, minBuildHeight, clampedTop - 1);

            for (int blockY = yStart; blockY >= yEnd; blockY--) {
                if (isCollidableBlock(cachedChunk, blockX, blockY, blockZ, cache.mutablePos)) {
                    double blockEntry = Math.max(lower, ((blockY + 1.0) - startY) / dirY);
                    return blockEntry <= upper ? blockEntry : -1.0;
                }
            }
            return -1.0;
        }

        int blockY = Mth.floor(startY);
        if (blockY < minBuildHeight || blockY >= clampedTop) {
            return -1.0;
        }
        return isCollidableBlock(cachedChunk, blockX, blockY, blockZ, cache.mutablePos) ? lower : -1.0;
    }

    private static boolean isCollidableBlock(CachedChunk chunk, int blockX, int blockY, int blockZ, BlockPos.MutableBlockPos mutablePos) {
        mutablePos.set(blockX, blockY, blockZ);
        return !chunk.chunk.getBlockState(mutablePos).getCollisionShape(chunk.chunk, mutablePos).isEmpty();
    }

    private static CachedChunk getChunkCached(Level level, int chunkX, int chunkZ, ScanCache cache) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        CachedChunk cachedChunk = cache.chunks.get(chunkKey);
        if (cachedChunk != null) {
            return cachedChunk;
        }
        if (cache.missingChunks.contains(chunkKey)) {
            return null;
        }

        if (!level.hasChunk(chunkX, chunkZ)) {
            cache.missingChunks.add(chunkKey);
            return null;
        }

        ChunkAccess loadedChunk = level.getChunk(chunkX, chunkZ);
        CachedChunk wrappedChunk = new CachedChunk(loadedChunk);
        cache.chunks.put(chunkKey, wrappedChunk);
        return wrappedChunk;
    }

    private static final class ScanCache {
        private final int minBuildHeight;
        private final int maxBuildHeight;
        private final Long2ObjectOpenHashMap<CachedChunk> chunks = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet missingChunks = new LongOpenHashSet();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private ScanCache(int minBuildHeight, int maxBuildHeight) {
            this.minBuildHeight = minBuildHeight;
            this.maxBuildHeight = maxBuildHeight;
        }
    }

    private static final class CachedChunk {
        private final ChunkAccess chunk;
        private final int[] heights = new int[16 * 16];

        private CachedChunk(ChunkAccess chunk) {
            this.chunk = chunk;
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    heights[(localZ << 4) | localX] = chunk.getHeight(HEIGHTMAP_TYPE, localX, localZ) + 1;
                }
            }
        }
    }
}
