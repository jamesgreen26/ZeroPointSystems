package g_mungus.zps.reactor;

import g_mungus.zps.mixin.ArrayVoxelShapeAccessor;
import g_mungus.zps.mixin.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A reactor cavity as a vanilla {@link VoxelShape}: whole blocks, in world coordinates. These are
 * the few things vanilla does not offer on a shape directly — building one from a set of block
 * cells, and walking its cells and open faces as block positions.
 */
public final class CavityShapes {

    private CavityShapes() {
    }

    /** The shape covering exactly the given block cells. */
    public static VoxelShape fromCells(LongSet cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("A cavity has at least one cell");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (LongIterator it = cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            int x = BlockPos.getX(cell), y = BlockPos.getY(cell), z = BlockPos.getZ(cell);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        BitSetDiscreteVoxelShape grid = new BitSetDiscreteVoxelShape(sizeX, sizeY, sizeZ);
        for (LongIterator it = cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            grid.fill(BlockPos.getX(cell) - minX, BlockPos.getY(cell) - minY, BlockPos.getZ(cell) - minZ);
        }
        return ArrayVoxelShapeAccessor.zps$create(grid, coords(minX, sizeX), coords(minY, sizeY), coords(minZ, sizeZ));
    }

    private static DoubleList coords(int start, int size) {
        double[] values = new double[size + 1];
        for (int i = 0; i <= size; i++) {
            values[i] = start + i;
        }
        return DoubleArrayList.wrap(values);
    }

    /** The cell grid behind the shape. */
    public static DiscreteVoxelShape grid(VoxelShape shape) {
        return ((VoxelShapeAccessor) shape).zps$getShape();
    }

    /** The block position of grid cell (0, 0, 0): the shape's lowest corner, rounded down. */
    public static BlockPos origin(VoxelShape shape) {
        AABB bounds = shape.bounds();
        return new BlockPos(Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ));
    }

    /**
     * The lowest cell, ordered by x, then y, then z. Matches how the server picks a reactor's
     * host cell, so both sides agree on which chunk a reactor belongs to.
     */
    public static BlockPos lowestCell(VoxelShape shape) {
        DiscreteVoxelShape grid = grid(shape);
        BlockPos origin = origin(shape);
        for (int x = 0; x < grid.getSize(Direction.Axis.X); x++) {
            for (int y = 0; y < grid.getSize(Direction.Axis.Y); y++) {
                for (int z = 0; z < grid.getSize(Direction.Axis.Z); z++) {
                    if (grid.isFull(x, y, z)) {
                        return origin.offset(x, y, z);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Empty shape");
    }

    /** Every open face of every cell, as the cell's block position and the side that is open. */
    public static void forAllFaces(VoxelShape shape, FaceConsumer consumer) {
        BlockPos origin = origin(shape);
        grid(shape).forAllFaces((direction, x, y, z) ->
                consumer.accept(direction, origin.getX() + x, origin.getY() + y, origin.getZ() + z));
    }

    /** Number of cells in the shape. */
    public static int cellCount(VoxelShape shape) {
        DiscreteVoxelShape grid = grid(shape);
        int count = 0;
        for (int x = 0; x < grid.getSize(Direction.Axis.X); x++) {
            for (int y = 0; y < grid.getSize(Direction.Axis.Y); y++) {
                for (int z = 0; z < grid.getSize(Direction.Axis.Z); z++) {
                    if (grid.isFull(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Whether the shape is one vanilla can hand its internals out for. */
    public static boolean isSupported(VoxelShape shape) {
        return shape instanceof ArrayVoxelShape && grid(shape) instanceof BitSetDiscreteVoxelShape;
    }

    @FunctionalInterface
    public interface FaceConsumer {
        void accept(Direction direction, int x, int y, int z);
    }
}
