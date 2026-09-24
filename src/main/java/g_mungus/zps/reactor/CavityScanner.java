package g_mungus.zps.reactor;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Finds sealed cavities: air regions enclosed on every side by reactor wall blocks.
 *
 * <p>Pure geometry, independent of the world, so it can be unit tested on a map. A flood fill
 * walks air from a start cell; it succeeds only if every cell it meets is air or wall and the
 * region stays within the size limit. Walls are the wall blocks face-adjacent to an interior
 * cell — a second layer outside the shell is not part of the cavity, and a wall between two
 * cavities belongs to both.
 */
public final class CavityScanner {

    /** What a cell is, as far as the scanner cares. */
    public enum Cell {
        AIR,
        WALL,
        /** Anything else, including positions outside the world. */
        OTHER
    }

    private CavityScanner() {
    }

    /**
     * Flood-fill from {@code start}.
     *
     * @param cells     what is at each position
     * @param maxExtent largest allowed interior size along any axis
     * @return the cavity, or null if the region is not sealed, is too large, or the start is not air
     */
    public static @Nullable CavityScan scan(BlockPos start, Function<BlockPos, Cell> cells, int maxExtent) {
        return scan(start, cells, maxExtent, new LongOpenHashSet());
    }

    /**
     * Every distinct sealed cavity touching {@code changed}: the ones its air neighbours are in,
     * plus the one it is in itself if it is now air. Cells visited by a failed fill are never
     * walked again, so an open field around the block costs one fill, not seven.
     */
    public static List<CavityScan> scanAround(BlockPos changed, Function<BlockPos, Cell> cells, int maxExtent) {
        List<CavityScan> found = new ArrayList<>();
        LongSet visited = new LongOpenHashSet();

        List<BlockPos> starts = new ArrayList<>(7);
        starts.add(changed);
        for (Direction direction : Direction.values()) {
            starts.add(changed.relative(direction));
        }

        for (BlockPos start : starts) {
            if (visited.contains(start.asLong()) || cells.apply(start) != Cell.AIR) {
                continue;
            }
            CavityScan scan = scan(start, cells, maxExtent, visited);
            if (scan != null) {
                found.add(scan);
            }
        }
        return found;
    }

    private static @Nullable CavityScan scan(BlockPos start, Function<BlockPos, Cell> cells, int maxExtent,
                                             LongSet visited) {
        if (cells.apply(start) != Cell.AIR) {
            return null;
        }

        LongSet interior = new LongOpenHashSet();
        LongSet walls = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        int minX = start.getX(), maxX = start.getX();
        int minY = start.getY(), maxY = start.getY();
        int minZ = start.getZ(), maxZ = start.getZ();
        BlockPos host = start;

        long startKey = start.asLong();
        visited.add(startKey);
        interior.add(startKey);
        queue.enqueue(startKey);

        boolean sealed = true;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            cursor.set(current);

            for (Direction direction : Direction.values()) {
                next.setWithOffset(cursor, direction);
                long key = next.asLong();

                Cell cell = cells.apply(next);
                if (cell == Cell.WALL) {
                    walls.add(key);
                    continue;
                }
                if (cell != Cell.AIR) {
                    // A hole in the shell. Keep walking so the visited set covers the whole open
                    // region and no other start has to discover the same leak.
                    sealed = false;
                    continue;
                }
                if (!visited.add(key)) {
                    continue;
                }

                minX = Math.min(minX, next.getX());
                maxX = Math.max(maxX, next.getX());
                minY = Math.min(minY, next.getY());
                maxY = Math.max(maxY, next.getY());
                minZ = Math.min(minZ, next.getZ());
                maxZ = Math.max(maxZ, next.getZ());
                if (maxX - minX >= maxExtent || maxY - minY >= maxExtent || maxZ - minZ >= maxExtent) {
                    // Too big to ever seal into a reactor. The fill would run on into open
                    // country; everything reached so far is already marked, which is enough.
                    return null;
                }

                interior.add(key);
                queue.enqueue(key);
                if (isBefore(next, host)) {
                    host = next.immutable();
                }
            }
        }

        return sealed ? new CavityScan(interior, walls, host) : null;
    }

    private static boolean isBefore(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return a.getX() < b.getX();
        }
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY();
        }
        return a.getZ() < b.getZ();
    }
}
