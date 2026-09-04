package g_mungus.zps.reactor;

import net.minecraft.util.Mth;

/**
 * The ratings a reactor gets from its shape. Pure functions of the counts so they can be tested
 * without a world or a config; the manager feeds them the configured constants.
 */
public final class ReactorGeometry {

    /** Interior volume of the reference "compact small reactor" a burst base is quoted for. */
    public static final double REFERENCE_VOLUME = 27.0;

    private ReactorGeometry() {
    }

    /**
     * How much interior the shell encloses per wall block, relative to the cube of the same volume.
     * A cube scores 1.0; anything longer, flatter, or more sprawling scores lower.
     */
    public static double compactness(int volume, int wallCount) {
        if (volume <= 0 || wallCount <= 0) {
            return 0;
        }
        double cubeWalls = 6.0 * Math.pow(volume, 2.0 / 3.0);
        return Math.min(1.0, cubeWalls / wallCount);
    }

    /** Larger reactors rate a little lower at the same compactness. Never below half. */
    public static double sizeFactor(int volume, double sizeExponent) {
        if (volume <= 0) {
            return 1.0;
        }
        return Mth.clamp(Math.pow(REFERENCE_VOLUME / volume, sizeExponent), 0.5, 1.0);
    }

    /** Pressure at which the chamber bursts, in Pascals. */
    public static double burstPressure(int volume, int wallCount, double basePressure,
                                       double compactnessExponent, double sizeExponent) {
        return basePressure
                * Math.pow(compactness(volume, wallCount), compactnessExponent)
                * sizeFactor(volume, sizeExponent);
    }

    /** The wall's thermal mass, in joules per kelvin. */
    public static double wallHeatCapacity(int wallCount, double perBlock) {
        return wallCount * perBlock;
    }
}
