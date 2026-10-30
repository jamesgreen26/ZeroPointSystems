package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.world.AtmosphericBiome;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * The compositions biome modifiers gave the server's biomes ({@code AtmosphericCompositionModifier}),
 * by biome id, handed to the client's biomes on login so its sky and weather thin as the server's air does.
 */
public record AtmosphericCompositionS2CPacket(Map<ResourceLocation, AtmosphericComposition> compositions)
        implements CustomPacketPayload {

    public static final Type<AtmosphericCompositionS2CPacket> TYPE = new Type<>(ZPSMod.resource("atmospheric_composition"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphericCompositionS2CPacket> STREAM_CODEC = ByteBufCodecs
            .<RegistryFriendlyByteBuf, ResourceLocation, AtmosphericComposition, Map<ResourceLocation, AtmosphericComposition>>map(
                    HashMap::new, ResourceLocation.STREAM_CODEC, AtmosphericComposition.STREAM_CODEC)
            .map(AtmosphericCompositionS2CPacket::new, AtmosphericCompositionS2CPacket::compositions);

    public static void handle(AtmosphericCompositionS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> apply(context.player().level().registryAccess().registryOrThrow(Registries.BIOME), packet.compositions()));
    }

    /** Give each biome in {@code biomes} named in {@code compositions} its composition; ids it lacks are skipped. */
    public static void apply(Registry<Biome> biomes, Map<ResourceLocation, AtmosphericComposition> compositions) {
        compositions.forEach((id, composition) -> biomes.getOptional(id)
                .ifPresent(biome -> ((AtmosphericBiome) (Object) biome).zps$setAtmosphericComposition(composition)));
    }

    @Override
    public Type<AtmosphericCompositionS2CPacket> type() {
        return TYPE;
    }
}
