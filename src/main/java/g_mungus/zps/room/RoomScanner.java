package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Finds sealed rooms: regions air cannot leave, judged by collision shape.
 *
 * <p>Air moves between neighbouring cells across the face they share, and is stopped only where
 * the two collision shapes together leave no open part of it in common. A slab or a stair is
 * walked into and seals only the faces it fills; a plate across a cell, flush with a face or not,
 * seals one side from the other; an open door has no collision across the doorway and so is a hole. {@link #AIR_PERMEABLE} blocks let air through
 * whatever their shape, and {@link #AIRTIGHT} ones seal whatever theirs.
 *
 * <p>Pure geometry over a shape lookup, so it can be unit tested on a map.
 */
public final class RoomScanner {

    /** Blocks air passes through regardless of their collision shape, such as iron bars. */
    public static final TagKey<Block> AIR_PERMEABLE = TagKey.create(Registries.BLOCK, ZPSMod.resource("air_permeable"));
    /** Blocks that seal as a full cube regardless of their collision shape. Empty by default, for packs to fill. */
    public static final TagKey<Block> AIRTIGHT = TagKey.create(Registries.BLOCK, ZPSMod.resource("airtight"));

    private RoomScanner() {
    }

    /**
     * Flood-fill from {@code start}.
     *
     * <p>The fill walks pockets of air, not whole cells — see {@link CellRegions} — so a plate across
     * the middle of a cell seals as well as one against its face. A cell counts as interior once any
     * pocket of it is reached. It rises as it goes ({@link RisingQueue}), so open air reaches the
     * top of the world and gives up long before it would reach the size limit.
     *
     * @param shapes    the collision shape at each position, or null for a hole (outside the world, unloaded)
     * @param maxVolume largest allowed interior, in cells
     * @return the room, or null if air escapes, the region is too large, or {@code start} is solid
     */
    public static @Nullable RoomScan scan(BlockPos start, Function<BlockPos, @Nullable VoxelShape> shapes, int maxVolume) {
        VoxelShape startShape = shapes.apply(start);
        if (startShape == null) {
            return null;
        }
        CellRegions startRegions = CellRegions.of(startShape);
        if (startRegions.count() == 0) {
            return null;
        }

        LongSet interior = new LongOpenHashSet();
        LongSet boundary = new LongOpenHashSet();
        // Which pockets of each cell have been reached, one bit each.
        Long2LongOpenHashMap reached = new Long2LongOpenHashMap();
        // Every pocket of each cell that has more than one, to tell which are only partly reached.
        Long2LongOpenHashMap split = new Long2LongOpenHashMap();
        RisingQueue queue = new RisingQueue();

        // Gas let out into a cell of several pockets could be in any of them.
        long startKey = start.asLong();
        interior.add(startKey);
        noteSplit(split, startKey, startRegions);
        for (int region = 0; region < startRegions.count(); region++) {
            reached.put(startKey, reached.get(startKey) | 1L << region);
            queue.enqueue(startKey, region);
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            long current = queue.dequeue();
            int region = queue.region();
            cursor.set(current);
            CellRegions here = CellRegions.of(shapes.apply(cursor));

            for (Direction direction : Direction.values()) {
                next.setWithOffset(cursor, direction);
                long key = next.asLong();
                VoxelShape thereShape = shapes.apply(next);
                if (thereShape == null) {
                    return null;
                }
                CellRegions there = CellRegions.of(thereShape);
                boolean crossed = false;
                for (int otherRegion = 0; otherRegion < there.count(); otherRegion++) {
                    if (!here.connects(region, direction, there, otherRegion)) {
                        continue;
                    }
                    crossed = true;
                    long bit = 1L << otherRegion;
                    long seen = reached.get(key);
                    if ((seen & bit) != 0) {
                        continue;
                    }
                    reached.put(key, seen | bit);
                    if (interior.add(key)) {
                        if (interior.size() > maxVolume) {
                            return null;
                        }
                        noteSplit(split, key, there);
                    }
                    queue.enqueue(key, otherRegion);
                }
                if (!crossed) {
                    boundary.add(key);
                }
            }
        }

        // A partial block can stop air from one side and still be reached from another.
        boundary.removeAll(interior);
        Long2LongOpenHashMap pockets = new Long2LongOpenHashMap();
        for (Long2LongMap.Entry entry : split.long2LongEntrySet()) {
            long mask = reached.get(entry.getLongKey());
            if (mask != entry.getLongValue()) {
                pockets.put(entry.getLongKey(), mask);
            }
        }
        return new RoomScan(interior, boundary, pockets);
    }

    private static void noteSplit(Long2LongMap split, long cell, CellRegions regions) {
        if (regions.count() > 1) {
            split.put(cell, allPockets(regions));
        }
    }

    /** Every pocket of a cell, one bit each. */
    static long allPockets(CellRegions regions) {
        return regions.count() >= Long.SIZE ? -1L : (1L << regions.count()) - 1;
    }

    /** What {@link #grow} found. */
    public enum Growth {
        /** Air from the room reached only sealed space: the room takes it in. */
        GREW,
        /** Air from the room got out, or the room would be past the size limit. */
        ESCAPED,
        /** Could not be settled from the changed cells alone; flood the room afresh. */
        UNSURE
    }

    /**
     * Grow a sealed room through cells that have just opened up — lost material, gained none — without
     * flooding it all again.
     *
     * <p>Taking material away can only join space, never part it, so the room's own air is still
     * one sealed whole; all that is new is wherever it can now reach through the changed cells. The
     * fill starts from the room's own pockets beside each changed cell, goes only into air the room
     * did not already hold, and stops wherever it meets the room again. Cost follows the new space,
     * not the room.
     *
     * @param interior the room's cells, untouched; see {@link RoomScan}
     * @param pockets  the room's partly-held cells, untouched; see {@link RoomScan}
     * @param changed  the cells that opened up, every one of them having only lost material
     * @param added    filled with the cells the room now also holds
     * @param boundary filled with cells newly stopping the room's air
     * @param pocketsAfter filled with the pockets now held of every cell the fill went into, changed
     *                     cells of the room included, whose old pockets no longer mean anything;
     *                     {@link #WHOLE} for a cell now wholly in the room
     */
    public static Growth grow(LongSet interior, Long2LongMap pockets, LongSet changed,
                              Function<BlockPos, @Nullable VoxelShape> shapes, int maxVolume,
                              LongSet added, LongSet boundary, Long2LongMap pocketsAfter) {
        Long2LongOpenHashMap reached = new Long2LongOpenHashMap();
        RisingQueue queue = new RisingQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        // Seed: every pocket of a changed cell that the room's own air, next door, now reaches.
        for (LongIterator it = changed.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            cursor.set(cell);
            VoxelShape shape = shapes.apply(cursor);
            if (shape == null) {
                return Growth.UNSURE;
            }
            CellRegions here = CellRegions.of(shape);
            for (Direction direction : Direction.values()) {
                next.setWithOffset(cursor, direction);
                long neighbour = next.asLong();
                if (!interior.contains(neighbour) || changed.contains(neighbour)) {
                    continue;
                }
                VoxelShape neighbourShape = shapes.apply(next);
                if (neighbourShape == null) {
                    return Growth.UNSURE;
                }
                CellRegions there = CellRegions.of(neighbourShape);
                long held = pockets.containsKey(neighbour) ? pockets.get(neighbour) : allPockets(there);
                for (int from = 0; from < there.count(); from++) {
                    if ((held & 1L << from) == 0) {
                        continue;
                    }
                    for (int to = 0; to < here.count(); to++) {
                        if (there.connects(from, direction.getOpposite(), here, to)
                                && enter(cell, to, interior, pockets, changed, reached, added, queue)
                                && interior.size() + added.size() > maxVolume) {
                            return Growth.ESCAPED;
                        }
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            long current = queue.dequeue();
            int region = queue.region();
            cursor.set(current);
            CellRegions here = CellRegions.of(shapes.apply(cursor));

            for (Direction direction : Direction.values()) {
                next.setWithOffset(cursor, direction);
                long key = next.asLong();
                VoxelShape thereShape = shapes.apply(next);
                if (thereShape == null) {
                    return Growth.ESCAPED;
                }
                CellRegions there = CellRegions.of(thereShape);
                boolean crossed = false;
                for (int otherRegion = 0; otherRegion < there.count(); otherRegion++) {
                    if (!here.connects(region, direction, there, otherRegion)) {
                        continue;
                    }
                    crossed = true;
                    if (enter(key, otherRegion, interior, pockets, changed, reached, added, queue)
                            && interior.size() + added.size() > maxVolume) {
                        return Growth.ESCAPED;
                    }
                }
                if (!crossed && !interior.contains(key)) {
                    boundary.add(key);
                }
            }
        }

        // A changed cell of the room that its own air no longer reaches cannot be told about from here.
        for (LongIterator it = changed.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (interior.contains(cell) && reached.get(cell) == 0) {
                return Growth.UNSURE;
            }
        }
        boundary.removeAll(added);
        for (Long2LongMap.Entry entry : reached.long2LongEntrySet()) {
            long cell = entry.getLongKey();
            long held = entry.getLongValue();
            if (interior.contains(cell) && !changed.contains(cell) && pockets.containsKey(cell)) {
                held |= pockets.get(cell);
            }
            cursor.set(cell);
            pocketsAfter.put(cell, held == allPockets(CellRegions.of(shapes.apply(cursor))) ? WHOLE : held);
        }
        return Growth.GREW;
    }

    /** In {@link #grow}'s results, a cell now wholly in the room. */
    public static final long WHOLE = -1L;

    /**
     * Air reaches pocket {@code region} of {@code cell}. Unless the room already held it, mark it
     * and queue it.
     *
     * @return whether this is a cell the room did not hold before, reached for the first time
     */
    private static boolean enter(long cell, int region, LongSet interior, Long2LongMap pockets, LongSet changed,
                                 Long2LongMap reached, LongSet added, RisingQueue queue) {
        long bit = 1L << region;
        boolean known = interior.contains(cell) && !changed.contains(cell);
        if (known && (!pockets.containsKey(cell) || (pockets.get(cell) & bit) != 0)) {
            return false;
        }
        long seen = reached.get(cell);
        if ((seen & bit) != 0) {
            return false;
        }
        reached.put(cell, seen | bit);
        queue.enqueue(cell, region);
        return !interior.contains(cell) && added.add(cell);
    }

    /** Whether a cell shaped {@code shape} has any room for air at all. */
    public static boolean holdsAir(VoxelShape shape) {
        return CellRegions.of(shape).count() > 0;
    }

    /** The shape lookup for a real level: holes outside the world and in unloaded chunks. */
    public static Function<BlockPos, @Nullable VoxelShape> shapes(ServerLevel level) {
        return pos -> {
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                return null;
            }
            return shapeOf(level, pos, level.getBlockState(pos));
        };
    }

    public static VoxelShape shapeOf(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(AIR_PERMEABLE)) {
            return Shapes.empty();
        }
        if (state.is(AIRTIGHT)) {
            return Shapes.block();
        }
        return state.getCollisionShape(level, pos);
    }
}
