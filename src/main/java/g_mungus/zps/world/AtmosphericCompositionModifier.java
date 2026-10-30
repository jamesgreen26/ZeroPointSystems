package g_mungus.zps.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.networking.AtmosphericCompositionS2CPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
 * Files go in {@code data/<ns>/forge/biome_modifier/}. Modifiers run on the server only, after the registries load, and the biomes the client builds
 * carry nothing of them; so each player is sent the changed compositions as they log in
 * ({@link AtmosphericComposition#overrides}).
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public record AtmosphericCompositionModifier(HolderSet<Biome> biomes, AtmosphericComposition composition) implements BiomeModifier {

    public static final Codec<AtmosphericCompositionModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AtmosphericCompositionModifier::biomes),
            AtmosphericComposition.BANDS_CODEC.fieldOf("composition").forGetter(AtmosphericCompositionModifier::composition)
    ).apply(instance, AtmosphericCompositionModifier::new));

    public static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ZPSMod.MOD_ID);

    public static final RegistryObject<Codec<AtmosphericCompositionModifier>> SERIALIZER =
            SERIALIZERS.register("atmospheric_composition", () -> CODEC);

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.AFTER_EVERYTHING && biomes.contains(biome)) {
            ((AtmosphericBiome) (Object) biome.value()).zps$setAtmosphericComposition(composition);
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var overrides = AtmosphericComposition.overrides(player.server.registryAccess());
            if (!overrides.isEmpty()) {
                ZPSGamePackets.sendToPlayer(player, new AtmosphericCompositionS2CPacket(overrides));
            }
        }
    }
}
