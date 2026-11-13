package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/**
 * The player is now in a room on the moving grid {@code grid}, or in none. Rooms are known only to
 * the server, while a player is moved by its own client, which needs this to have the grid carry
 * the player along; see {@code Compat#carryWithRoom}. Sent only on a change.
 */
public record RoomGridS2CPacket(Optional<UUID> grid) implements CustomPacketPayload {

    public static final Type<RoomGridS2CPacket> TYPE = new Type<>(ZPSMod.resource("room_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomGridS2CPacket> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).map(RoomGridS2CPacket::new, RoomGridS2CPacket::grid).cast();

    public static void handle(RoomGridS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Compat.setLocalPlayerRoomGrid(packet.grid().orElse(null)));
    }

    @Override
    public Type<RoomGridS2CPacket> type() {
        return TYPE;
    }
}
