package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The player inside a duct has asked for the next or previous vent.
 *
 * <p>Carries only a direction: which vent that lands on is the server's decision, so a client
 * cannot name a destination it was never offered.
 */
public record DuctCycleC2SPacket(int delta) implements CustomPacketPayload {

    public static final Type<DuctCycleC2SPacket> TYPE = new Type<>(ZPSMod.resource("duct_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuctCycleC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuctCycleC2SPacket::delta,
                    DuctCycleC2SPacket::new);

    public static void handle(DuctCycleC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
                return;
            }
            // One step either way, whatever the client claimed to send.
            duct.requestCycle(packet.delta() < 0 ? -1 : 1);
        });
    }

    @Override
    public Type<DuctCycleC2SPacket> type() {
        return TYPE;
    }
}
