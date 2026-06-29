package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.ContraptionPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when a player places a held block onto a contraption face (possibly nested). */
public record ContraptionPlaceC2SPacket(ContraptionPath path, BlockPos localPos, Direction localFace, Vec3 localHit,
        InteractionHand hand) implements CustomPacketPayload {
    public static final Type<ContraptionPlaceC2SPacket> TYPE = new Type<>(ZPSMod.resource("contraption_place"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionPlaceC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionPlaceC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            ContraptionPath path = ContraptionPath.STREAM_CODEC.decode(buffer);
            BlockPos localPos = buffer.readBlockPos();
            Direction localFace = buffer.readEnum(Direction.class);
            Vec3 localHit = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            return new ContraptionPlaceC2SPacket(path, localPos, localFace, localHit, hand);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionPlaceC2SPacket packet) {
            ContraptionPath.STREAM_CODEC.encode(buffer, packet.path);
            buffer.writeBlockPos(packet.localPos);
            buffer.writeEnum(packet.localFace);
            buffer.writeDouble(packet.localHit.x);
            buffer.writeDouble(packet.localHit.y);
            buffer.writeDouble(packet.localHit.z);
            buffer.writeEnum(packet.hand);
        }
    };

    public static void handle(ContraptionPlaceC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            ServoMotorBlockEntity motor = packet.path.resolve(level);
            if (motor == null) return;
            motor.placeContraptionBlock(packet.localPos, packet.localFace, packet.localHit, sender, packet.hand);
        });
    }

    @Override
    public Type<ContraptionPlaceC2SPacket> type() {
        return TYPE;
    }
}
