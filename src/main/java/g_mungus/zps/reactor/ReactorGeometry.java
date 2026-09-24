package g_mungus.zps.reactor;

/**
 * The ratings a reactor gets from its shape. Pure functions of the counts so they can be tested
 * without a world or a config; the manager feeds them the configured constants.
 */
public final class ReactorGeometry {

    private ReactorGeometry() {
    }

    /** The wall's thermal mass, in joules per kelvin. */
    public static double wallHeatCapacity(int wallCount, double perBlock) {
        return wallCount * perBlock;
    }
}
