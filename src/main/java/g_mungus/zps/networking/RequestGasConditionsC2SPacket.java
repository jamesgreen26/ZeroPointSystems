package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.gas.AtmosphereBreather;
import g_mungus.zps.gas.api.GasConditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The debug screen is open and wants the gas conditions the server last applied to the player. */
public record RequestGasConditionsC2SPacket() implements CustomPacketPayload {

    public static final Type<RequestGasConditionsC2SPacket> TYPE = new Type<>(ZPSMod.resource("request_gas_conditions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGasConditionsC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestGasConditionsC2SPacket());

    public static void handle(RequestGasConditionsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ((AtmosphereBreather) player).zps$getGasConditions() instanceof GasConditions conditions) {
                PacketDistributor.sendToPlayer(player, new GasConditionsS2CPacket(conditions));
            }
        });
    }

    @Override
    public Type<RequestGasConditionsC2SPacket> type() {
        return TYPE;
    }
}
