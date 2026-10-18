package g_mungus.zps.networking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A sealed room came open at {@code pos}: a burst of its gas blows out through the hole along
 * {@code outward}. {@code massKg} is the share of the room's gas lost through this hole.
 */
public record RoomBreachS2CPacket(BlockPos pos, Direction outward, ResourceLocation gas, float massKg) {

    public static void encode(RoomBreachS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.outward);
        buffer.writeResourceLocation(packet.gas);
        buffer.writeFloat(packet.massKg);
    }

    public static RoomBreachS2CPacket decode(FriendlyByteBuf buffer) {
        return new RoomBreachS2CPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                buffer.readResourceLocation(), buffer.readFloat());
    }

    public static void handle(RoomBreachS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> g_mungus.zps.client.ClientPacketHandlers.roomBreach(packet));
        context.setPacketHandled(true);
    }
}
