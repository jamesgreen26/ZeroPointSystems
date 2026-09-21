package g_mungus.zps.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Implemented by falling blocks, so that the client eases them onto the position the server reports instead of
 * jumping there.
 *
 * <p>The client moves a falling block itself, with the same physics as the server, and the server's word on where
 * it is arrives once a second, or every tick while a Tractor Beam carries it. The game takes that word by putting
 * the block there on the spot, between two frames. The two sides tick at the same rate but not at the same
 * moment, so the client has often ticked the block once more or once less than the state in the packet, and the
 * block is thrown a whole tick of travel forward or back: two thirds of a block after a second of falling, more
 * after that.
 *
 * <p>So being one tick early or late is not counted as being wrong, and what is left is made up over the next
 * few ticks, inside the tick, where the renderer smooths it like any other movement.
 */
public interface EasedSync {

    /** How many ticks a correction is spread over. The game uses the same for mobs. */
    int TICKS = 3;

    /** Further out than this the block really has been moved somewhere else, and goes there at once. */
    double SNAP_DISTANCE = 4.0;

    /**
     * Called on the client with the position the server reports.
     *
     * @return false to have the game put the entity there as it normally would
     */
    boolean zps$easeToward(double x, double y, double z);

    /**
     * What is still wrong with a position that is off by {@code error}, once being a tick early or late is
     * allowed for. {@code step} is how far the entity moves in a tick.
     */
    static Vec3 residual(Vec3 error, Vec3 step) {
        Vec3 best = error;
        // A tick early, the client is a step past the reported position. A tick late, a step short of it.
        for (Vec3 candidate : new Vec3[]{error.add(step), error.subtract(step)}) {
            if (candidate.lengthSqr() < best.lengthSqr()) {
                best = candidate;
            }
        }
        return best;
    }
}
