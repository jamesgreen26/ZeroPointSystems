package g_mungus.zps.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.networking.AtmosphericCompositionS2CPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The {@code zps:atmospheric_composition} biome modifier: gives every biome in {@code biomes} the
 * composition it holds, in place of what the biome's file says. Biomes are picked as any biome
 * modifier picks them, by id, list or tag, so a datapack can give another mod's biomes an
 * atmosphere without replacing their files:
 * <pre>{@code
 * {
 *   "type": "zps:atmospheric_composition",
 *   "biomes": "#c:is_moon",
 *   "composition": {
 *     "*": { "zps:vacuum": 1.0 }
 *   }
 * }
 * }</pre>
 * Modifiers run on the server only, after the registries load, and the biomes the client builds
 * carry nothing of them; so each player is sent the changed compositions as they log in
 * ({@link AtmosphericComposition#overrides}).
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public record AtmosphericCompositionModifier(HolderSet<Biome> biomes, AtmosphericComposition composition) implements BiomeModifier {

    public static final MapCodec<AtmosphericCompositionModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AtmosphericCompositionModifier::biomes),
            AtmosphericComposition.BANDS_CODEC.fieldOf("composition").forGetter(AtmosphericCompositionModifier::composition)
    ).apply(instance, AtmosphericCompositionModifier::new));

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ZPSMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<AtmosphericCompositionModifier>> SERIALIZER =
            SERIALIZERS.register("atmospheric_composition", () -> CODEC);

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.AFTER_EVERYTHING && biomes.contains(biome)) {
            ((AtmosphericBiome) (Object) biome.value()).zps$setAtmosphericComposition(composition);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var overrides = AtmosphericComposition.overrides(player.server.registryAccess());
            if (!overrides.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new AtmosphericCompositionS2CPacket(overrides));
            }
        }
    }
}
