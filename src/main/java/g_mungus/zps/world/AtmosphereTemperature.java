package g_mungus.zps.world;

import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * The {@code zps:atmosphere} temperature modifier, added to {@link Biome.TemperatureModifier} by
 * {@code TemperatureModifierMixin}. A biome using it keeps its climate temperature at the bottom of
 * the atmosphere and cools linearly to {@link #MIN_TEMPERATURE} at {@link #TOP_Y}, holding there
 * above. It replaces vanilla's height falloff rather than adding to it (see {@code BiomeMixin}).
 */
public final class AtmosphereTemperature {
    public static final String NAME = "ZPS_ATMOSPHERE";
    public static final String SERIALIZED_NAME = "zps:atmosphere";

    /** Where the overworld's build limit ends and its atmosphere begins. */
    public static final int BOTTOM_Y = 320;
    public static final int TOP_Y = 2048;
    public static final float MIN_TEMPERATURE = -1.0f;

    public static final Biome.TemperatureModifier MODIFIER = Biome.TemperatureModifier.valueOf(NAME);

    private AtmosphereTemperature() {}

    public static float temperature(int y, float climateTemperature) {
        float progress = Mth.clamp((y - BOTTOM_Y) / (float) (TOP_Y - BOTTOM_Y), 0.0f, 1.0f);
        return Mth.lerp(progress, climateTemperature, MIN_TEMPERATURE);
    }
}
