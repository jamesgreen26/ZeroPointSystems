package g_mungus.zps.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Covers {@link AtmosphericComposition}: loaded from every biome file, and its codec's handling of one. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AtmosphericCompositionGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final ResourceLocation AETHER = ResourceLocation.fromNamespaceAndPath("zps", "aether");
    private static final ResourceLocation VACUUM = ZPSMod.resource("vacuum");

    @GameTest(template = TEMPLATE)
    public static void everyBiomeFile_hasAnEntry(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var compositions = registries.registryOrThrow(AtmosphericComposition.REGISTRY_KEY);
        var biomes = registries.registryOrThrow(Registries.BIOME);
        for (var key : biomes.registryKeySet()) {
            if (!compositions.containsKey(key.location())) helper.fail("No atmospheric composition entry for " + key.location());
            if (AtmosphericComposition.of(biomes.getOrThrow(key)) != compositions.get(key.location())) {
                helper.fail(key.location() + " was not handed its registry entry");
            }
        }
        if (!AtmosphericComposition.of(biomes.getOrThrow(Biomes.PLAINS)).at(64).equals(AtmosphericComposition.PLAIN_AIR)) {
            helper.fail("Plains has no composition field, so it should be plain air");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void overworldAtmosphere_isVacuumFrom2048(GameTestHelper helper) {
        AtmosphericComposition composition = AtmosphericComposition.of(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.BIOME).getOrThrow(ResourceKey.create(Registries.BIOME, ZPSMod.resource("overworld_atmosphere"))));
        expect(helper, composition.at(2047), AtmosphericComposition.PLAIN_AIR, "y=2047");
        expect(helper, composition.at(2048), Map.of(VACUUM, 1.0), "y=2048");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void biomeJson_readsOnlyCompositionField(GameTestHelper helper) {
        AtmosphericComposition composition = decode("""
                {
                  "has_precipitation": false,
                  "temperature": 0.8,
                  "downfall": 0.0,
                  "effects": { "sky_color": 7907327 },
                  "zps:atmospheric_composition": {
                    "320..1023": { "kelvin:air": 2 },
                    "1024..*": { "kelvin:air": 1, "zps:aether": 3 }
                  }
                }
                """).getOrThrow(false, error -> {});
        expect(helper, composition.at(500), Map.of(AtmosphericComposition.AIR, 1.0), "y=500");
        expect(helper, composition.at(5000), Map.of(AtmosphericComposition.AIR, 0.25, AETHER, 0.75), "y=5000");
        expect(helper, composition.at(0), AtmosphericComposition.PLAIN_AIR, "y=0, outside every band");

        if (decode("""
                { "zps:atmospheric_composition": { "0..100": { "kelvin:air": 1 }, "100..*": { "kelvin:air": 1 } } }
                """).result().isPresent()) {
            helper.fail("Overlapping bands should be rejected");
        }
        helper.succeed();
    }

    private static com.mojang.serialization.DataResult<AtmosphericComposition> decode(String json) {
        JsonElement element = JsonParser.parseString(json);
        return AtmosphericComposition.CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static void expect(GameTestHelper helper, Map<ResourceLocation, Double> actual, Map<ResourceLocation, Double> expected, String where) {
        if (!actual.equals(expected)) helper.fail("Composition at " + where + " should be " + expected + ", was " + actual);
    }
}
