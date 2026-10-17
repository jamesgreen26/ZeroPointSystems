package g_mungus.zps.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    ).apply(instance, SkyBiomeSettings::new)).validate(SkyBiomeSettings::checkOverlaps);

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

    private static DataResult<SkyBiomeSettings> checkOverlaps(SkyBiomeSettings settings) {
        List<YRange> ranges = new ArrayList<>(settings.biomes().keySet());
        ranges.sort(Comparator.comparingInt(YRange::min));
        for (int i = 1; i < ranges.size(); i++) {
            YRange previous = ranges.get(i - 1);
            YRange current = ranges.get(i);
            if (current.min() <= previous.max()) {
                return DataResult.error(() -> "Overlapping Y ranges " + previous + " and " + current);
            }
        }
        return DataResult.success(settings);
    }

    /** An inclusive Y band, written as {@code "min..max"} or a single number; {@code *} leaves an end unbounded. */
    public record YRange(int min, int max) {
        public static final Codec<YRange> CODEC = Codec.STRING.comapFlatMap(YRange::parse, YRange::toString);

        public boolean contains(int y) {
            return y >= min && y <= max;
        }

        private static DataResult<YRange> parse(String text) {
            if (text.trim().equals("*")) return DataResult.success(new YRange(Integer.MIN_VALUE, Integer.MAX_VALUE));
            int separator = text.indexOf("..");
            try {
                int min = parseEnd(separator < 0 ? text : text.substring(0, separator), Integer.MIN_VALUE);
                int max = separator < 0 ? min : parseEnd(text.substring(separator + 2), Integer.MAX_VALUE);
                if (min > max) return DataResult.error(() -> "Y range " + text + " has min above max");
                return DataResult.success(new YRange(min, max));
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Not a Y range (expected \"min..max\"): " + text);
            }
        }

        private static int parseEnd(String end, int unbounded) {
            end = end.trim();
            return end.equals("*") ? unbounded : Integer.parseInt(end);
        }

        private static String formatEnd(int end, int unbounded) {
            return end == unbounded ? "*" : Integer.toString(end);
        }

        @Override
        public String toString() {
            if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) return "*";
            if (min == max) return Integer.toString(min);
            return formatEnd(min, Integer.MIN_VALUE) + ".." + formatEnd(max, Integer.MAX_VALUE);
        }
    }
}
