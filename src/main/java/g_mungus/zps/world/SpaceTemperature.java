package g_mungus.zps.world;

import g_mungus.zps.gas.api.HeatSource;
import g_mungus.zps.gas.api.HeatSourceProvider;
import g_mungus.zps.gas.api.HeatSources;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The {@code zps:space} temperature modifier, added to {@link Biome.TemperatureModifier} by
 * {@code TemperatureModifierMixin}. A biome using it holds its climate temperature at every height
 * (see {@code BiomeMixin}) as the cold background of open space, and is heated by the
 * {@link HeatSource}s registered with {@link HeatSources}.
 *
 * <p>Vanilla's temperature has no level to find heat sources in and is cached by position, so heat is
 * added only where ZPS reads an atmosphere's temperature ({@code AtmosphereExposure.temperatureK}).
 * Vanilla's own freezing and snow see just the background.
 */
public final class SpaceTemperature {
    public static final String NAME = "ZPS_SPACE";
    public static final String SERIALIZED_NAME = "zps:space";

    public static final Biome.TemperatureModifier MODIFIER = Biome.TemperatureModifier.valueOf(NAME);

    private SpaceTemperature() {}

    public static boolean appliesTo(Biome biome) {
        return biome.getModifiedClimateSettings().temperatureModifier() == MODIFIER;
    }

    /**
     * The temperature at {@code pos} in {@code level}, from {@code backgroundK} and every heat source
     * there. Radiation adds, so the temperatures combine as the fourth root of the sum of their fourth
     * powers.
     */
    public static double kelvin(Level level, BlockPos pos, double backgroundK) {
        List<HeatSourceProvider> providers = HeatSources.providers();
        if (providers.isEmpty()) {
            return backgroundK;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        double flux = pow4(backgroundK);
        for (HeatSourceProvider provider : providers) {
            for (HeatSource source : provider.sources(level)) {
                flux += pow4(source.temperatureAt(at.distanceTo(source.position())));
            }
        }
        return Math.sqrt(Math.sqrt(flux));
    }

    private static double pow4(double value) {
        double squared = value * value;
        return squared * squared;
    }
}
