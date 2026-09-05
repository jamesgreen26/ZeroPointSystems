package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.reactor.ClientReactors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A reactor's cavity, for the client to draw. Sent when a reactor forms and whenever a player
 * starts tracking the chunk its host cell is in.
 *
 * @param shape the cavity, in world coordinates, whole blocks
 * @param heat  chamber temperature over ignition temperature, so the glow starts at the right level
 */
public record ReactorShapeS2CPacket(int id, VoxelShape shape, float heat) implements CustomPacketPayload {

    public static final Type<ReactorShapeS2CPacket> TYPE = new Type<>(ZPSMod.resource("reactor_shape"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorShapeS2CPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ReactorShapeS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new ReactorShapeS2CPacket(
                            buffer.readVarInt(),
                            VoxelShapeStreamCodec.INSTANCE.decode(buffer),
                            buffer.readFloat());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ReactorShapeS2CPacket packet) {
                    buffer.writeVarInt(packet.id());
                    VoxelShapeStreamCodec.INSTANCE.encode(buffer, packet.shape());
                    buffer.writeFloat(packet.heat());
                }
            };

    public static void handle(ReactorShapeS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientReactors.accept(packet));
    }

    @Override
    public Type<ReactorShapeS2CPacket> type() {
        return TYPE;
    }
}
