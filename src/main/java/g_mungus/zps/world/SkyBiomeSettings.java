package g_mungus.zps.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.ZPSMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Which biomes take up which Y bands of a dimension. The entry's id is the dimension it applies to,
 * so the overworld's settings live at {@code data/minecraft/zps/sky_biome_settings/overworld.json}:
 * <pre>{@code
 * {
 *   "biomes": {
 *     "320..511": "minecraft:snowy_plains",
 *     "512..1023": "minecraft:the_end"
 *   }
 * }
 * }</pre>
 * Ranges are inclusive at both ends and may not overlap; a single number is a one-block band.
 * Either end may be {@code *} for unbounded, and {@code "*"} alone covers every height.
 * Bands only take effect above the build limit (see {@code ChunkAccessMixin}); there, heights
 * outside every band read the top section's biome as in vanilla.
 */
public record SkyBiomeSettings(Map<YRange, Holder<Biome>> biomes) {
    public static final ResourceKey<Registry<SkyBiomeSettings>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ZPSMod.resource("sky_biome_settings"));

    public static final Codec<SkyBiomeSettings> CODEC = RecordCodecBuilder.<SkyBiomeSettings>create(instance -> instance.group(
            Codec.unboundedMap(YRange.CODEC, RegistryFixedCodec.create(Registries.BIOME)).fieldOf("biomes").forGetter(SkyBiomeSettings::biomes)
    ).apply(instance, SkyBiomeSettings::new)).validate(settings -> YRange.checkNoOverlap(settings.biomes()).map(ok -> settings));

    public SkyBiomeSettings {
        biomes = Map.copyOf(biomes);
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, CODEC, CODEC);
    }

    /** The biome whose band contains block height {@code y}, or null if none does. */
    public @Nullable Holder<Biome> biomeAt(int y) {
        for (Map.Entry<YRange, Holder<Biome>> entry : biomes.entrySet()) {
            if (entry.getKey().contains(y)) return entry.getValue();
        }
        return null;
    }

    /** The settings for {@code level}'s dimension, cached on the level since biome lookups are hot. */
    public static @Nullable SkyBiomeSettings forLevel(Level level) {
        return ((SkyBiomeLevel) level).zps$skyBiomeSettings();
    }

    /** The settings for {@code dimension}, if a datapack defines any. */
    public static Optional<SkyBiomeSettings> forDimension(RegistryAccess registries, ResourceKey<Level> dimension) {
        return registries.registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(dimension.location()));
    }
}
