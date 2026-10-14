package g_mungus.zps.blockentity;

/**
 * A block entity whose block wears the redstone band around its base plate, the Serial Bus and the
 * Redstone Converter, which also lights its front window. Read on the client, so the level has to
 * travel with the update packet.
 */
public interface RedstoneBandSource {

    /** The redstone level the band shows, 0 to 15. */
    int getBandLevel();

    /** Whether the facing face carries the converter's window, lit along with the band. */
    default boolean hasFrontWindow() {
        return false;
    }
}
