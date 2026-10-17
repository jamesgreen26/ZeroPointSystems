package g_mungus.zps.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.ZPSMod;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What gases make up the air of a biome, by height. Read from the biome's own JSON: this registry
 * loads from {@code worldgen/biome} (see {@code RegistriesMixin}) and its codec only looks at one
 * extra field, which the biome codec ignores. Every biome file therefore has an entry, and one
 * without the field is plain air at every height:
 * <pre>{@code
 * {
 *   "temperature": 0.8,
 *   ...
 *   "zps:atmospheric_composition": {
 *     "320..1023": { "kelvin:air": 1.0 },
 *     "1024..*": { "kelvin:air": 0.4, "zps:aether": 0.6 }
 *   }
 * }
 * }</pre>
 * Bands use {@link YRange}'s syntax and may not overlap; heights outside every band are plain air.
 * Each band's fractions are normalised to sum to 1. Gases are held by id, like recipe outputs, so
 * one no mod registers is only an unresolved id rather than a failed biome.
 */
public record AtmosphericComposition(Map<YRange, Map<ResourceLocation, Double>> bands) {
    public static final ResourceKey<Registry<AtmosphericComposition>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ZPSMod.resource("atmospheric_composition"));
    public static final String FIELD = "zps:atmospheric_composition";

    public static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath("kelvin", "air");
    /** Not a registered gas, but still something an entity is exposed to: see {@code AtmosphereExposure}. */
    public static final ResourceLocation VACUUM = ZPSMod.resource("vacuum");
    public static final Map<ResourceLocation, Double> PLAIN_AIR = Map.of(AIR, 1.0);
    /** A biome whose file has no composition field: plain air at every height. */
    public static final AtmosphericComposition DEFAULT = new AtmosphericComposition(Map.of(YRange.ALL, PLAIN_AIR));

    private static final Codec<Map<ResourceLocation, Double>> GASES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC,
                    Codec.DOUBLE.validate(fraction -> fraction > 0
                            ? DataResult.success(fraction)
                            : DataResult.error(() -> "Gas fractions must be positive, got " + fraction)))
            .validate(gases -> gases.isEmpty()
                    ? DataResult.error(() -> "A band needs at least one gas")
                    : DataResult.success(gases));

    public static final Codec<AtmosphericComposition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(YRange.CODEC, GASES_CODEC).validate(YRange::checkNoOverlap)
                    .optionalFieldOf(FIELD, DEFAULT.bands()).forGetter(AtmosphericComposition::bands)
    ).apply(instance, AtmosphericComposition::new));

    public AtmosphericComposition {
        Map<YRange, Map<ResourceLocation, Double>> normalised = new HashMap<>();
        bands.forEach((range, gases) -> normalised.put(range, normalise(gases)));
        bands = Map.copyOf(normalised);
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, CODEC, CODEC);
    }

    /** The gas fractions at block height {@code y}, summing to 1. */
    public Map<ResourceLocation, Double> at(int y) {
        for (Map.Entry<YRange, Map<ResourceLocation, Double>> entry : bands.entrySet()) {
            if (entry.getKey().contains(y)) return entry.getValue();
        }
        return PLAIN_AIR;
    }

    /** {@code biome}'s composition, or {@link #DEFAULT} for a biome that has no file. */
    public static AtmosphericComposition of(Biome biome) {
        return ((AtmosphericBiome) (Object) biome).zps$getAtmosphericComposition();
    }

    /**
     * Hands every biome in {@code registries} its composition. Called as each registry load finishes,
     * on the server and on the client, so the biomes are tagged before anything can ask them.
     */
    public static void attachToBiomes(RegistryAccess registries) {
        Optional<Registry<Biome>> biomes = registries.registry(Registries.BIOME);
        Optional<Registry<AtmosphericComposition>> compositions = registries.registry(REGISTRY_KEY);
        if (biomes.isEmpty() || compositions.isEmpty()) return;
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomes.get().entrySet()) {
            AtmosphericComposition composition = compositions.get().getOptional(entry.getKey().location()).orElse(DEFAULT);
            ((AtmosphericBiome) (Object) entry.getValue()).zps$setAtmosphericComposition(composition);
        }
    }

    private static Map<ResourceLocation, Double> normalise(Map<ResourceLocation, Double> gases) {
        double total = gases.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<ResourceLocation, Double> normalised = new HashMap<>();
        gases.forEach((gas, fraction) -> normalised.put(gas, fraction / total));
        return Map.copyOf(normalised);
    }
}
