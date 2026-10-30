package g_mungus.zps.networking;

import g_mungus.zps.world.AtmosphericBiome;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The compositions biome modifiers gave the server's biomes ({@code AtmosphericCompositionModifier}),
 * by biome id, handed to the client's biomes on login so its sky and weather thin as the server's air does.
 */
public record AtmosphericCompositionS2CPacket(Map<ResourceLocation, AtmosphericComposition> compositions) {

    public static void encode(AtmosphericCompositionS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeMap(packet.compositions, FriendlyByteBuf::writeResourceLocation, (out, composition) -> composition.write(out));
    }

    public static AtmosphericCompositionS2CPacket decode(FriendlyByteBuf buffer) {
        return new AtmosphericCompositionS2CPacket(buffer.readMap(FriendlyByteBuf::readResourceLocation, AtmosphericComposition::read));
    }

    public static void handle(AtmosphericCompositionS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> g_mungus.zps.client.ClientPacketHandlers.atmosphericComposition(packet));
        context.setPacketHandled(true);
    }

    /** Give each biome in {@code biomes} named in {@code compositions} its composition; ids it lacks are skipped. */
    public static void apply(Registry<Biome> biomes, Map<ResourceLocation, AtmosphericComposition> compositions) {
        compositions.forEach((id, composition) -> biomes.getOptional(id)
                .ifPresent(biome -> ((AtmosphericBiome) (Object) biome).zps$setAtmosphericComposition(composition)));
    }
}
