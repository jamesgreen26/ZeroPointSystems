package g_mungus.zps.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.api.HeatSource;
import g_mungus.zps.gas.api.HeatSourceProvider;
import g_mungus.zps.gas.api.HeatSources;
import g_mungus.zps.world.BiomeTemperature;
import g_mungus.zps.world.SpaceTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Covers {@link SpaceTemperature}: the {@code zps:space} modifier and heat from {@link HeatSources}. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpaceTemperatureGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void spaceModifier_parsesFromItsName(GameTestHelper helper) {
        Biome.TemperatureModifier parsed = Biome.TemperatureModifier.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive(SpaceTemperature.SERIALIZED_NAME))
                .result().orElse(null);
        if (parsed != SpaceTemperature.MODIFIER) {
            helper.fail("\"" + SpaceTemperature.SERIALIZED_NAME + "\" should parse to the space modifier, got " + parsed);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void heatSources_heatSpaceAndNothingElse(GameTestHelper helper) {
        BlockPos star = helper.absolutePos(new BlockPos(3, 2, 3));
        // Only this test's level; other tests in it don't use zps:space biomes, so they aren't heated
        HeatSourceProvider provider = level -> level == helper.getLevel()
                ? List.of(new HeatSource(Vec3.atCenterOf(star), 950, 10))
                : List.of();
        HeatSources.register(provider);
        try {
            double atSurface = SpaceTemperature.kelvin(helper.getLevel(), star, 200);
            if (Math.abs(atSurface - Math.pow(Math.pow(200, 4) + Math.pow(950, 4), 0.25)) > 0.01) {
                helper.fail("At a source, its heat and the background should combine radiatively, got " + atSurface + " K");
            }
            double farAway = SpaceTemperature.kelvin(helper.getLevel(), star.offset(100_000, 0, 0), 200);
            if (farAway > 200.01) {
                helper.fail("Far from every source, only the background should be left, got " + farAway + " K");
            }
            double biome = BiomeTemperature.kelvinAt(helper.getLevel().getBiome(star).value(), star);
            double read = AtmosphereExposure.temperatureK(helper.getLevel(), star);
            if (read != biome) {
                helper.fail("A biome not using zps:space should not be heated, got " + read + " K instead of " + biome + " K");
            }
        } finally {
            HeatSources.unregister(provider);
        }
        helper.succeed();
    }
}
