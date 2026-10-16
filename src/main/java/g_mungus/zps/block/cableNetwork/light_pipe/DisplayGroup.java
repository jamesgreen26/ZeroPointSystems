package g_mungus.zps.block.cableNetwork.light_pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A merged text display: a {@code width x height} rectangle of blocks in the plane
 * of their face, with {@code topLeft} the block at the viewer's top left.
 */
public record DisplayGroup(BlockPos topLeft, Direction facing, int width, int height) {

    /** The group of that size in which the block at {@code pos} sits at {@code column, row}. */
    public DisplayGroup(BlockPos pos, Direction facing, int width, int height, int column, int row) {
        this(pos.relative(right(facing), -column).above(row), facing, width, height);
    }

    /** The group a display block's state says it belongs to. */
    public static DisplayGroup of(BlockState state, BlockPos pos) {
        DisplayLayout layout = state.getValue(TextDisplayBlock.LAYOUT);
        return new DisplayGroup(pos, state.getValue(TextDisplayBlock.FACING),
                layout.width, layout.height, layout.column, layout.row);
    }

    /** The viewer's right, for someone looking at a display that faces {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public BlockPos cell(int column, int row) {
        return topLeft.relative(right(facing), column).below(row);
    }

    /** All blocks of the group, in reading order. */
    public List<BlockPos> cells() {
        List<BlockPos> cells = new ArrayList<>(width * height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                cells.add(cell(column, row));
            }
        }
        return cells;
    }

    /** Whether {@code other} lies wholly inside this group. */
    public boolean contains(DisplayGroup other) {
        if (other.facing != facing) {
            return false;
        }
        BlockPos offset = other.topLeft.subtract(topLeft);
        Direction right = right(facing);
        if (offset.get(facing.getAxis()) != 0) {
            return false;
        }
        int column = offset.get(right.getAxis()) * right.getAxisDirection().getStep();
        int row = -offset.getY();
        return column >= 0 && row >= 0 && column + other.width <= width && row + other.height <= height;
    }
}
