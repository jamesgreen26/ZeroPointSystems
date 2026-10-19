package g_mungus.zps.networking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A sealed room came open at {@code pos}: a burst of its gas blows out through the hole along
 * {@code outward}. {@code massKg} is the share of the room's gas lost through this hole, and
 * {@code buoyancy} how it floats in the air outside (see {@code RoomManager#buoyancy}). Drawn with
 * the vent's particles, whatever the gas.
 */
public record RoomBreachS2CPacket(BlockPos pos, Direction outward, float massKg, float buoyancy) {

    public static void encode(RoomBreachS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.outward);
        buffer.writeFloat(packet.massKg);
        buffer.writeFloat(packet.buoyancy);
    }

    public static RoomBreachS2CPacket decode(FriendlyByteBuf buffer) {
        return new RoomBreachS2CPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(RoomBreachS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> g_mungus.zps.client.ClientPacketHandlers.roomBreach(packet));
        context.setPacketHandled(true);
    }
}
