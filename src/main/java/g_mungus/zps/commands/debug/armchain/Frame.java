package g_mungus.zps.commands.debug.armchain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A local grid laid over the world: {@code forward} steps along {@link #forward}, {@code side} steps along
 * {@link #side}, {@code up} is up. Lets a set be written once and built facing any horizontal direction.
 */
public record Frame(BlockPos origin, Direction forward, Direction side) {

    public Frame {
        if (forward.getAxis().isVertical() || side.getAxis() == forward.getAxis() || side.getAxis().isVertical()) {
            throw new IllegalArgumentException("A frame needs two different horizontal axes, got " + forward + " and " + side);
        }
    }

    public BlockPos at(int forward, int up, int side) {
        return origin.offset(offset(forward, up, side));
    }

    /** The world offset of a step in this frame, as a whole-block vector. */
    public BlockPos offset(int forward, int up, int side) {
        return new BlockPos(
                this.forward.getStepX() * forward + this.side.getStepX() * side,
                up,
                this.forward.getStepZ() * forward + this.side.getStepZ() * side);
    }

    public Frame moved(int forward, int up, int side) {
        return new Frame(at(forward, up, side), this.forward, this.side);
    }

    public Direction back() {
        return forward.getOpposite();
    }

    public Direction otherSide() {
        return side.getOpposite();
    }
}
