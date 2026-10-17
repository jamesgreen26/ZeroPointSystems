package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.gas.AtmosphereBreather;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The player has started or stopped drowning in gas. Air runs on both sides, and a client that did
 * not know would refill what the server drains, flickering the bubbles; see {@code GasExposure}.
 * Sent only on a change.
 */
public record DrowningS2CPacket(boolean drowning) implements CustomPacketPayload {

    public static final Type<DrowningS2CPacket> TYPE = new Type<>(ZPSMod.resource("drowning"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DrowningS2CPacket> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(DrowningS2CPacket::new, DrowningS2CPacket::drowning).cast();

    public static void handle(DrowningS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ((AtmosphereBreather) context.player()).zps$setSyncedDrowning(packet.drowning()));
    }

    @Override
    public Type<DrowningS2CPacket> type() {
        return TYPE;
    }
}
