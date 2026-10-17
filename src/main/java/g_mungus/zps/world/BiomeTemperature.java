package g_mungus.zps.world;

import g_mungus.zps.mixin.BiomeInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/**
 * Converts Minecraft's unitless biome temperature to kelvin, linearly. A biome temperature of
 * {@link #FREEZING_BIOME_TEMPERATURE} is {@link #FREEZING_K}, the default
 * {@code EntityFreezeGasTemperature}, and plains (0.8) sit at 20 °C. That puts the hottest vanilla
 * biomes (2.0: desert, badlands, the Nether) at about 50 °C, nowhere near the default
 * {@code EntityBurnGasTemperature}.
 */
public final class BiomeTemperature {
    public static final float FREEZING_BIOME_TEMPERATURE = -0.9f;
    public static final double FREEZING_K = 250.0;
    /** Kelvin per unit of biome temperature, from plains' 0.8 being 20 °C. */
    public static final double KELVIN_PER_UNIT = (293.15 - FREEZING_K) / (0.8 - FREEZING_BIOME_TEMPERATURE);

    private BiomeTemperature() {}

    public static double toKelvin(float biomeTemperature) {
        return FREEZING_K + (biomeTemperature - FREEZING_BIOME_TEMPERATURE) * KELVIN_PER_UNIT;
    }

    /** {@code biome}'s temperature at {@code pos} in kelvin, height falloff and temperature modifier included. */
    public static double kelvinAt(Biome biome, BlockPos pos) {
        return toKelvin(((BiomeInvoker) (Object) biome).zps$getTemperature(pos));
    }
}
