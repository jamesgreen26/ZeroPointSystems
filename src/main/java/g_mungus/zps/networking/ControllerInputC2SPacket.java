package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.ControllerSeatEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Which of a controller's keys the rider is holding, bit 0 being the first channel. Sent on change, and again every half second. */
public record ControllerInputC2SPacket(int pressed) implements CustomPacketPayload {
    public static final Type<ControllerInputC2SPacket> TYPE = new Type<>(ZPSMod.resource("controller_input"));
    public static final StreamCodec<ByteBuf, ControllerInputC2SPacket> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ControllerInputC2SPacket::new, ControllerInputC2SPacket::pressed);

    public static void handle(ControllerInputC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof ControllerSeatEntity seat && seat.blockEntity != null) {
                seat.blockEntity.setPressed(packet.pressed);
            }
        });
    }

    @Override
    public Type<ControllerInputC2SPacket> type() {
        return TYPE;
    }
}
