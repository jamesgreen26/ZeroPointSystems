package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A reactor is gone: unsealed, breached, or burst. */
public record ReactorRemovedS2CPacket(int id) implements CustomPacketPayload {

    public static final Type<ReactorRemovedS2CPacket> TYPE = new Type<>(ZPSMod.resource("reactor_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorRemovedS2CPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ReactorRemovedS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new ReactorRemovedS2CPacket(buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ReactorRemovedS2CPacket packet) {
                    buffer.writeVarInt(packet.id());
                }
            };

    public static void handle(ReactorRemovedS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientReactors.remove(packet.id()));
    }

    @Override
    public Type<ReactorRemovedS2CPacket> type() {
        return TYPE;
    }
}
