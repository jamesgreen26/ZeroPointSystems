package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.world.BiomeTemperature;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Covers {@link BiomeTemperature}'s anchor points against the vanilla biomes. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BiomeTemperatureGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void freezingPoint_isMinusPointNine(GameTestHelper helper) {
        double kelvin = BiomeTemperature.toKelvin(-0.9f);
        if (Math.abs(kelvin - 250.0) > 1.0e-3) helper.fail("-0.9 should be 250 K, was " + kelvin);
        double plains = BiomeTemperature.toKelvin(0.8f);
        if (Math.abs(plains - 293.15) > 1.0e-3) helper.fail("0.8 should be 293.15 K, was " + plains);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noVanillaBiome_burnsPlayers(GameTestHelper helper) {
        double burn = ZPSConfig.entityBurnGasTemperatureK();
        helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).entrySet().forEach(entry -> {
            if (!entry.getKey().location().getNamespace().equals("minecraft")) return;
            double kelvin = BiomeTemperature.toKelvin(entry.getValue().getBaseTemperature());
            if (kelvin >= burn) helper.fail(entry.getKey().location() + " is " + kelvin + " K, at or over the " + burn + " K burn threshold");
        });
        helper.succeed();
    }
}
