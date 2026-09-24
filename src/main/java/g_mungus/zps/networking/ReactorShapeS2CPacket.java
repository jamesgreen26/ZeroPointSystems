package g_mungus.zps.networking;

import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A reactor's cavity, for the client to draw. Sent when a reactor forms and whenever a player
 * starts tracking the chunk its host cell is in.
 *
 * @param shape the cavity, in world coordinates, whole blocks
 * @param heat  chamber temperature over ignition temperature, so the glow starts at the right level
 */
public record ReactorShapeS2CPacket(int id, VoxelShape shape, float heat) {

    public static void encode(ReactorShapeS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.id());
        VoxelShapeStreamCodec.INSTANCE.encode(buffer, packet.shape());
        buffer.writeFloat(packet.heat());
    }

    public static ReactorShapeS2CPacket decode(FriendlyByteBuf buffer) {
        return new ReactorShapeS2CPacket(buffer.readVarInt(), VoxelShapeStreamCodec.INSTANCE.decode(buffer), buffer.readFloat());
    }

    public static void handle(ReactorShapeS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientReactors.accept(packet));
        context.setPacketHandled(true);
    }
}
