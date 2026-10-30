package g_mungus.zps.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.networking.AtmosphericCompositionS2CPacket;
import g_mungus.zps.world.AtmosphericBiome;
import g_mungus.zps.world.AtmosphericComposition;
import g_mungus.zps.world.AtmosphericCompositionModifier;
import g_mungus.zps.world.YRange;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

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

    /** The overworld's air thins linearly from the build limit to where the vacuum starts at 2048. */
    @GameTest(template = TEMPLATE)
    public static void overworldDensity_fallsToVacuumAt2048(GameTestHelper helper) {
        BlockPos column = helper.absolutePos(BlockPos.ZERO);
        expectDensity(helper, column.atY(64), 1.0f);
        expectDensity(helper, column.atY(320), 1.0f);
        expectDensity(helper, column.atY(1184), 0.5f);
        expectDensity(helper, column.atY(2048), 0.0f);
        expectDensity(helper, column.atY(5000), 0.0f);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void density_isFullWithoutVacuum(GameTestHelper helper) {
        if (AtmosphericComposition.DEFAULT.density(5000, 320) != 1.0f) helper.fail("A biome with no vacuum should be full density everywhere");
        AtmosphericComposition allVacuum = new AtmosphericComposition(Map.of(YRange.ALL, Map.of(VACUUM, 1.0)));
        if (allVacuum.density(0, 320) != 0.0f) helper.fail("A biome of nothing but vacuum should have no density anywhere");
        helper.succeed();
    }

    private static void expectDensity(GameTestHelper helper, BlockPos pos, float expected) {
        float actual = AtmosphericComposition.getDensity(helper.getLevel(), pos);
        if (Math.abs(actual - expected) > 1.0e-4f) helper.fail("Density at y=" + pos.getY() + " should be " + expected + ", was " + actual);
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

    /** A modifier picks biomes by tag, and gives them its composition once every other modifier has run. */
    @GameTest(template = TEMPLATE)
    public static void biomeModifier_overridesTaggedBiomes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        Holder<Biome> ocean = biomes.getHolderOrThrow(Biomes.OCEAN);
        Holder<Biome> plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        AtmosphericComposition before = AtmosphericComposition.of(ocean.value());
        try {
            AtmosphericCompositionModifier modifier = AtmosphericCompositionModifier.CODEC.parse(
                    RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseString("""
                            {
                              "biomes": "#minecraft:is_ocean",
                              "composition": { "*": { "zps:vacuum": 1.0 } }
                            }
                            """)).getOrThrow(false, error -> helper.fail("The modifier should decode: " + error));
            for (BiomeModifier.Phase phase : BiomeModifier.Phase.values()) {
                if (phase == BiomeModifier.Phase.AFTER_EVERYTHING) continue;
                modifier.modify(ocean, phase, null);
            }
            expect(helper, AtmosphericComposition.of(ocean.value()).at(64), before.at(64), "the ocean before the last phase");

            modifier.modify(ocean, BiomeModifier.Phase.AFTER_EVERYTHING, null);
            modifier.modify(plains, BiomeModifier.Phase.AFTER_EVERYTHING, null);
            expect(helper, AtmosphericComposition.of(ocean.value()).at(64), Map.of(VACUUM, 1.0), "the ocean");
            expect(helper, AtmosphericComposition.of(plains.value()).at(64), AtmosphericComposition.PLAIN_AIR, "plains, not an ocean");

            var overrides = AtmosphericComposition.overrides(registries);
            if (!overrides.keySet().equals(Set.of(Biomes.OCEAN.location()))) {
                helper.fail("Only the ocean should be an override, got " + overrides.keySet());
            }
        } finally {
            ((AtmosphericBiome) (Object) ocean.value()).zps$setAtmosphericComposition(before);
        }
        helper.succeed();
    }

    /** The login packet carries compositions intact, and hands them to the biomes it names. */
    @GameTest(template = TEMPLATE)
    public static void compositionPacket_roundTripsAndApplies(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        AtmosphericComposition sent = new AtmosphericComposition(Map.of(
                new YRange(Integer.MIN_VALUE, 2047), Map.of(AtmosphericComposition.AIR, 0.25, AETHER, 0.75),
                new YRange(2048, Integer.MAX_VALUE), Map.of(VACUUM, 1.0)));
        ResourceLocation missing = ZPSMod.resource("no_such_biome");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        AtmosphericCompositionS2CPacket.encode(new AtmosphericCompositionS2CPacket(Map.of(Biomes.DESERT.location(), sent, missing, sent)), buffer);
        AtmosphericCompositionS2CPacket received = AtmosphericCompositionS2CPacket.decode(buffer);
        if (!sent.equals(received.compositions().get(Biomes.DESERT.location()))) {
            helper.fail("The composition should arrive as sent, got " + received.compositions());
        }

        Biome desert = registries.registryOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        AtmosphericComposition before = AtmosphericComposition.of(desert);
        try {
            AtmosphericCompositionS2CPacket.apply(registries.registryOrThrow(Registries.BIOME), received.compositions());
            expect(helper, AtmosphericComposition.of(desert).at(3000), Map.of(VACUUM, 1.0), "the desert at y=3000");
        } finally {
            ((AtmosphericBiome) (Object) desert).zps$setAtmosphericComposition(before);
        }
        helper.succeed();
    }
}
