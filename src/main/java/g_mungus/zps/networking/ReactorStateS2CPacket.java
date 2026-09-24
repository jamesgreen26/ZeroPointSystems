package g_mungus.zps.networking;

import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** How hot a reactor is, as chamber temperature over ignition temperature. */
public record ReactorStateS2CPacket(int id, float heat) {

    public static void encode(ReactorStateS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.id());
        buffer.writeFloat(packet.heat());
    }

    public static ReactorStateS2CPacket decode(FriendlyByteBuf buffer) {
        return new ReactorStateS2CPacket(buffer.readVarInt(), buffer.readFloat());
    }

    public static void handle(ReactorStateS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientReactors.setHeat(packet.id(), packet.heat()));
        context.setPacketHandled(true);
    }
}
