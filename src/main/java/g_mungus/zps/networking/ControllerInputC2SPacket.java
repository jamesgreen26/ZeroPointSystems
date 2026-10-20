package g_mungus.zps.networking;

import g_mungus.zps.entity.ControllerSeatEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Which of a controller's keys the rider is holding, bit 0 being the first channel. Sent on change, and again every half second. */
public record ControllerInputC2SPacket(int pressed) {

    public static void encode(ControllerInputC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.pressed);
    }

    public static ControllerInputC2SPacket decode(FriendlyByteBuf buffer) {
        return new ControllerInputC2SPacket(buffer.readVarInt());
    }

    public static void handle(ControllerInputC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null && sender.getVehicle() instanceof ControllerSeatEntity seat && seat.blockEntity != null) {
                seat.blockEntity.setPressed(packet.pressed);
            }
        });
        context.setPacketHandled(true);
    }
}
