package g_mungus.zps.networking;

import g_mungus.zps.compat.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player is now in a room on the ship {@code grid}, or in none ({@link #NONE}). Rooms are known
 * only to the server, while a player is dragged by its own client, which needs this to have the
 * ship drag the player along; see {@code Compat#carryWithRoom}. Sent only on a change.
 */
public record RoomGridS2CPacket(long grid) {

    /** No ship: Valkyrien Skies' own ship ids are never negative. */
    public static final long NONE = -1L;

    public static void encode(RoomGridS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.grid);
    }

    public static RoomGridS2CPacket decode(FriendlyByteBuf buffer) {
        return new RoomGridS2CPacket(buffer.readLong());
    }

    public static void handle(RoomGridS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Compat.setLocalPlayerRoomGrid(packet.grid()));
        context.setPacketHandled(true);
    }
}
