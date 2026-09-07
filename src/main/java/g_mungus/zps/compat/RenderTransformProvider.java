package g_mungus.zps.compat;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

/**
 * The frame-by-frame render transform of the moving grid a block position belongs to: a Sable
 * sublevel or a Valkyrien Skies ship. Grid mods keep their blocks in a far-off region of the
 * level and draw them somewhere else; this is the map from that region to where the blocks are
 * drawn this frame, interpolated to the frame's partial tick.
 *
 * <p>Bound to a position, not to a grid: the grid is looked up on every call. A grid mod can learn
 * of its grid after the blocks on it have arrived, as Sable's sublevels do on a rejoin, and can
 * replace its client-side grid object when the grid leaves and re-enters range.
 *
 * <p>Render thread only: the grid mods interpolate and cache their poses there. Take a copy of
 * the matrix before handing it to anything that runs off-thread, such as Flywheel's frame plans.
 */
@FunctionalInterface
public interface RenderTransformProvider {

    /**
     * Writes the grid's own block space to world space transform for the current frame into dest
     * and returns it, or returns null while the position is not on any grid.
     */
    @Nullable Matrix4d localToWorld(Matrix4d dest);
}
