package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
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
     * pocket of it is reached.
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
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        IntArrayFIFOQueue queuedRegions = new IntArrayFIFOQueue();

        // Gas let out into a cell of several pockets could be in any of them.
        long startKey = start.asLong();
        interior.add(startKey);
        for (int region = 0; region < startRegions.count(); region++) {
            reached.put(startKey, reached.get(startKey) | 1L << region);
            queue.enqueue(startKey);
            queuedRegions.enqueue(region);
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int region = queuedRegions.dequeueInt();
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
                    if (interior.add(key) && interior.size() > maxVolume) {
                        return null;
                    }
                    queue.enqueue(key);
                    queuedRegions.enqueue(otherRegion);
                }
                if (!crossed) {
                    boundary.add(key);
                }
            }
        }

        // A partial block can stop air from one side and still be reached from another.
        boundary.removeAll(interior);
        return new RoomScan(interior, boundary);
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
