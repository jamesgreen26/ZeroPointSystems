package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** How hot a reactor is, as chamber temperature over ignition temperature. */
public record ReactorStateS2CPacket(int id, float heat) implements CustomPacketPayload {

    public static final Type<ReactorStateS2CPacket> TYPE = new Type<>(ZPSMod.resource("reactor_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorStateS2CPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ReactorStateS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new ReactorStateS2CPacket(buffer.readVarInt(), buffer.readFloat());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ReactorStateS2CPacket packet) {
                    buffer.writeVarInt(packet.id());
                    buffer.writeFloat(packet.heat());
                }
            };

    public static void handle(ReactorStateS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientReactors.setHeat(packet.id(), packet.heat()));
    }

    @Override
    public Type<ReactorStateS2CPacket> type() {
        return TYPE;
    }
}
