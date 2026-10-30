package g_mungus.zps.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.util.CodecUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.DataPackRegistryEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
 * Bands only take effect above the build limit (see {@link #skyBiome}); there, heights outside every
 * band read the top section's biome as in vanilla.
 *
 * <p>The entry holds biome ids rather than biomes. 1.20.1 sends the synced registries in the login
 * packet with only the built-in registries to resolve against, so an entry naming a biome as a
 * holder cannot be encoded, and a dedicated server drops every joining player. Each level resolves the
 * ids against its own biomes once ({@link #forLevel}).
 */
public record SkyBiomeSettings(Map<YRange, ResourceKey<Biome>> biomes) {
    public static final ResourceKey<Registry<SkyBiomeSettings>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ZPSMod.resource("sky_biome_settings"));

    /** Read from datapacks: each biome must exist. Never written back out. */
    public static final Codec<SkyBiomeSettings> CODEC = codec(RegistryFixedCodec.create(Registries.BIOME).flatComapMap(
            holder -> holder.unwrapKey().orElseThrow(),
            key -> DataResult.error(() -> "Sky biome settings are only read from datapacks, not written")));
    /** Sent to clients: ids alone, which need no biome registry to encode. */
    public static final Codec<SkyBiomeSettings> NETWORK_CODEC = codec(ResourceKey.codec(Registries.BIOME));

    private static Codec<SkyBiomeSettings> codec(Codec<ResourceKey<Biome>> biome) {
        return CodecUtil.validate(RecordCodecBuilder.<SkyBiomeSettings>create(instance -> instance.group(
                Codec.unboundedMap(YRange.CODEC, biome).fieldOf("biomes").forGetter(SkyBiomeSettings::biomes)
        ).apply(instance, SkyBiomeSettings::new)), settings -> YRange.checkNoOverlap(settings.biomes()).map(ok -> settings));
    }

    /** The settings with each id resolved to its biome in one level's registries. */
    public record Resolved(Map<YRange, Holder<Biome>> biomes) {
        /** The biome whose band contains block height {@code y}, or null if none does. */
        public @Nullable Holder<Biome> biomeAt(int y) {
            for (Map.Entry<YRange, Holder<Biome>> entry : biomes.entrySet()) {
                if (entry.getKey().contains(y)) return entry.getValue();
            }
            return null;
        }
    }

    public SkyBiomeSettings {
        biomes = Map.copyOf(biomes);
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, CODEC, NETWORK_CODEC);
    }

    /** These settings against {@code registries}' biomes; a band whose biome is missing there is dropped. */
    public Resolved resolve(RegistryAccess registries) {
        Registry<Biome> registry = registries.registryOrThrow(Registries.BIOME);
        Map<YRange, Holder<Biome>> resolved = new HashMap<>();
        biomes.forEach((band, key) -> registry.getHolder(key).ifPresentOrElse(
                biome -> resolved.put(band, biome),
                () -> ZPSMod.LOGGER.warn("Sky biome band {} names {}, which is not a biome here", band, key.location())));
        return new Resolved(Map.copyOf(resolved));
    }

    /**
     * The band biome at quart height {@code quartY} in {@code level}, or null below the build limit or
     * outside every band. Read through loaded chunks ({@code ChunkAccessMixin}) and, where none is
     * loaded, through the level's uncached fallback ({@code ServerLevelMixin}, {@code ClientLevelMixin}),
     * so the sky keeps its biome with no terrain beneath it.
     */
    public static @Nullable Holder<Biome> skyBiome(Level level, int quartY) {
        int blockY = QuartPos.toBlock(quartY);
        if (blockY < level.getMaxBuildHeight()) return null;
        Resolved settings = forLevel(level);
        return settings == null ? null : settings.biomeAt(blockY);
    }

    /**
     * The nearest place to {@code from} whose sky band biome matches, for {@code /locate biome}: a band covers
     * every column, so straight up or down, at the nearest height in the band above the build limit. Null if
     * no band matches. Coming at a band from outside, the height is the middle of its nearest whole quart:
     * biomes are read a quart at a time, and a block lookup wanders up to half a quart from where it is asked,
     * so the edge of the band could read as what is beyond it.
     */
    public static @Nullable Pair<BlockPos, Holder<Biome>> closest(Level level, Predicate<Holder<Biome>> matches, BlockPos from) {
        Resolved settings = forLevel(level);
        if (settings == null) return null;
        Pair<BlockPos, Holder<Biome>> best = null;
        for (Map.Entry<YRange, Holder<Biome>> entry : settings.biomes().entrySet()) {
            YRange band = entry.getKey();
            int bottom = Math.max(band.min(), level.getMaxBuildHeight());
            if (bottom > band.max() || !matches.test(entry.getValue())) continue;
            // The band's whole quarts, a block in from each edge of them; skipped if it has none.
            int firstQuart = QuartPos.fromBlock(bottom + 3);
            int lastQuart = band.max() == Integer.MAX_VALUE ? Integer.MAX_VALUE / 4 : QuartPos.fromBlock(band.max() + 1) - 1;
            if (firstQuart > lastQuart) continue;
            int y = from.getY();
            if (y < QuartPos.toBlock(firstQuart)) {
                y = QuartPos.toBlock(firstQuart) + 2;
            } else if (y > QuartPos.toBlock(lastQuart) + 3) {
                y = QuartPos.toBlock(lastQuart) + 2;
            }
            if (best == null || Math.abs(y - from.getY()) < Math.abs(best.getFirst().getY() - from.getY())) {
                best = Pair.of(from.atY(y), entry.getValue());
            }
        }
        return best;
    }

    /** The settings for {@code level}'s dimension, resolved and cached on the level since biome lookups are hot. */
    public static @Nullable Resolved forLevel(Level level) {
        return ((SkyBiomeLevel) level).zps$skyBiomeSettings();
    }

    /** The settings for {@code dimension}, if a datapack defines any. */
    public static Optional<SkyBiomeSettings> forDimension(RegistryAccess registries, ResourceKey<Level> dimension) {
        return registries.registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(dimension.location()));
    }
}
