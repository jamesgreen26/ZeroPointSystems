package g_mungus.zps.client.model.connected;

public final class PiecedLayout {
    /** Number of {@code 16px} tiles a "pieced" strip must have. */
    public static final int TILE_COUNT = 5;

    private PiecedLayout() {}

    /**
     * @param h whether the horizontal neighbour adjacent to this corner connects
     * @param v whether the vertical neighbour adjacent to this corner connects
     * @param d whether the diagonal neighbour between them connects
     * @return the strip tile index (0-4) to sample this corner from
     */
    public static int tileIndex(boolean h, boolean v, boolean d) {
        if (!h && !v) {
            return 0; // both edges are borders meeting at a convex corner
        }
        if (!h) {
            return 2; // border on the horizontal side -> side edge
        }
        if (!v) {
            return 3; // border on the vertical side -> top/bottom edge
        }
        return d ? 1 : 4; // both edges connect: interior if the diagonal does too, else concave corner
    }
}
