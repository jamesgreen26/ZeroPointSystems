package g_mungus.zps.gas.api;

import net.minecraft.world.phys.Vec3;

/**
 * Something that heats the open space around it, such as a star, for biomes using the {@code zps:space}
 * temperature modifier. Supplied by a {@link HeatSourceProvider}.
 *
 * <p>Heat falls off radiatively: {@code surfaceTemperatureK} at {@code radius} from {@code position},
 * and in proportion to {@code √(radius / distance)} further out, so a larger source heats further away.
 * Within {@code radius} it holds at {@code surfaceTemperatureK}.
 *
 * @param position            the centre, in world coordinates of the level it is provided for
 * @param surfaceTemperatureK the temperature at {@code radius}, in kelvin
 * @param radius              where the source's surface is, in blocks; must be positive
 */
public record HeatSource(Vec3 position, double surfaceTemperatureK, double radius) {

    public HeatSource {
        if (!(radius > 0)) {
            throw new IllegalArgumentException("A heat source's radius must be positive, got " + radius);
        }
    }

    /** The temperature this source alone gives at {@code distance} from its centre, in kelvin. */
    public double temperatureAt(double distance) {
        return surfaceTemperatureK * Math.sqrt(radius / Math.max(radius, distance));
    }
}
