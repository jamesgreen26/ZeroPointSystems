package g_mungus.zps.networking;

import g_mungus.zps.client.DuctTravelClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The vents on the run the player is currently inside, and which of them they are looking out of.
 *
 * <p>Only the rider needs any of this, so it is pushed to that one player rather than synched on
 * the entity.
 *
 * <p>{@code orientTo} is present only when the client should turn the player to look straight out
 * of the grille, which is on climbing in and nowhere else: you enter facing the panel, so without
 * it you would arrive staring into the back of a duct. Once inside, where you are looking is yours
 * — taking it away mid-hop is disorienting — so a hop leaves it empty and the view is untouched.
 */
public record DuctTravelStateS2CPacket(List<BlockPos> destinations, int selected, Optional<Direction> orientTo) {

    public static void encode(DuctTravelStateS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.destinations(), FriendlyByteBuf::writeBlockPos);
        buffer.writeVarInt(packet.selected());
        buffer.writeOptional(packet.orientTo(), FriendlyByteBuf::writeEnum);
    }

    public static DuctTravelStateS2CPacket decode(FriendlyByteBuf buffer) {
        return new DuctTravelStateS2CPacket(
                buffer.readList(FriendlyByteBuf::readBlockPos),
                buffer.readVarInt(),
                buffer.readOptional(buf -> buf.readEnum(Direction.class)));
    }

    public static void handle(DuctTravelStateS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DuctTravelClientState.accept(packet));
        context.setPacketHandled(true);
    }
}
