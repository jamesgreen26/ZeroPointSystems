package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.debug.ClientGasConditions;
import g_mungus.zps.gas.api.GasConditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The gas conditions the server last applied to the player, for the debug screen. */
public record GasConditionsS2CPacket(GasConditions conditions) implements CustomPacketPayload {

    public static final Type<GasConditionsS2CPacket> TYPE = new Type<>(ZPSMod.resource("gas_conditions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasConditionsS2CPacket> STREAM_CODEC =
            GasConditions.STREAM_CODEC.map(GasConditionsS2CPacket::new, GasConditionsS2CPacket::conditions);

    public static void handle(GasConditionsS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientGasConditions.receive(packet.conditions()));
    }

    @Override
    public Type<GasConditionsS2CPacket> type() {
        return TYPE;
    }
}
