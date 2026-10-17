package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
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
 * the two collision shapes together cover that face completely — the same test vanilla uses for
 * light. A slab or a stair is walked into and seals only the faces it fills; an open door has no
 * collision across the doorway and so is a hole. {@link #AIR_PERMEABLE} blocks let air through
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
     * @param shapes    the collision shape at each position, or null for a hole (outside the world, unloaded)
     * @param maxVolume largest allowed interior, in cells
     * @return the room, or null if air escapes, the region is too large, or {@code start} is solid
     */
    public static @Nullable RoomScan scan(BlockPos start, Function<BlockPos, @Nullable VoxelShape> shapes, int maxVolume) {
        VoxelShape startShape = shapes.apply(start);
        if (startShape == null || startShape == Shapes.block()) {
            return null;
        }

        LongSet interior = new LongOpenHashSet();
        LongSet boundary = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        interior.add(start.asLong());
        queue.enqueue(start.asLong());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            cursor.set(current);
            VoxelShape here = shapes.apply(cursor);

            for (Direction direction : Direction.values()) {
                next.setWithOffset(cursor, direction);
                long key = next.asLong();
                if (interior.contains(key)) {
                    continue;
                }
                VoxelShape there = shapes.apply(next);
                if (there == null) {
                    return null;
                }
                if (seals(here, there, direction)) {
                    boundary.add(key);
                    continue;
                }
                interior.add(key);
                if (interior.size() > maxVolume) {
                    return null;
                }
                queue.enqueue(key);
            }
        }

        // A partial block can stop air from one side and still be reached from another.
        boundary.removeAll(interior);
        return new RoomScan(interior, boundary);
    }

    /** Whether air is stopped going from a cell shaped {@code from} into its neighbour on {@code side}. */
    static boolean seals(VoxelShape from, VoxelShape to, Direction side) {
        if (from.isEmpty() && to.isEmpty()) {
            return false;
        }
        return Shapes.mergedFaceOccludes(from, to, side);
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
