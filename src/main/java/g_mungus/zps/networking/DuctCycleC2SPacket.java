package g_mungus.zps.networking;

import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player inside a duct has asked for the next or previous vent.
 *
 * <p>Carries only a direction: which vent that lands on is the server's decision, so a client
 * cannot name a destination it was never offered.
 */
public record DuctCycleC2SPacket(int delta) {

    public static void encode(DuctCycleC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.delta());
    }

    public static DuctCycleC2SPacket decode(FriendlyByteBuf buffer) {
        return new DuctCycleC2SPacket(buffer.readVarInt());
    }

    public static void handle(DuctCycleC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.getVehicle() instanceof DuctTravelEntity duct)) {
                return;
            }
            // One step either way, whatever the client claimed to send.
            duct.requestCycle(packet.delta() < 0 ? -1 : 1);
        });
        context.setPacketHandled(true);
    }
}
