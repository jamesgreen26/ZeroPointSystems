package g_mungus.zps.networking;

import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A reactor is gone: unsealed, breached, or burst. */
public record ReactorRemovedS2CPacket(int id) {

    public static void encode(ReactorRemovedS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.id());
    }

    public static ReactorRemovedS2CPacket decode(FriendlyByteBuf buffer) {
        return new ReactorRemovedS2CPacket(buffer.readVarInt());
    }

    public static void handle(ReactorRemovedS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientReactors.remove(packet.id()));
        context.setPacketHandled(true);
    }
}
