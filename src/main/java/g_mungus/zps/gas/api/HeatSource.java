package g_mungus.zps.gas.api;

import net.minecraft.world.phys.Vec3;

/**
 * Something that heats the open space around it, such as a star, for biomes using the {@code zps:space}
 * temperature modifier. Supplied by a {@link HeatSourceProvider}.
 *
 * <p>Heat falls off with distance: {@code surfaceTemperatureK} at {@code radius} from {@code position},
 * and in proportion to {@code (radius / distance)}<sup>{@code falloff}</sup> further out, so a larger
 * source heats further away. Within {@code radius} it holds at {@code surfaceTemperatureK}.
 *
 * @param position            the centre, in world coordinates of the level it is provided for
 * @param surfaceTemperatureK the temperature at {@code radius}, in kelvin
 * @param radius              where the source's surface is, in blocks; must be positive
 * @param falloff             how steeply the heat drops with distance; must be positive.
 *                            {@link #RADIATIVE} is what radiation alone gives; more keeps a very hot
 *                            source from heating everything at the scale of a world
 */
public record HeatSource(Vec3 position, double surfaceTemperatureK, double radius, double falloff) {

    /** The falloff of radiated heat: temperature in proportion to {@code √(radius / distance)}. */
    public static final double RADIATIVE = 0.5;

    public HeatSource {
        if (!(radius > 0)) {
            throw new IllegalArgumentException("A heat source's radius must be positive, got " + radius);
        }
        if (!(falloff > 0)) {
            throw new IllegalArgumentException("A heat source's falloff must be positive, got " + falloff);
        }
    }

    /** A source whose heat falls off {@linkplain #RADIATIVE radiatively}. */
    public HeatSource(Vec3 position, double surfaceTemperatureK, double radius) {
        this(position, surfaceTemperatureK, radius, RADIATIVE);
    }

    /** The temperature this source alone gives at {@code distance} from its centre, in kelvin. */
    public double temperatureAt(double distance) {
        return surfaceTemperatureK * Math.pow(radius / Math.max(radius, distance), falloff);
    }
}
