package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
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

/** Sent when a player right-clicks (uses) a block on a contraption: buttons, levers, doors, etc. */
public record ContraptionUseC2SPacket(BlockPos motorPos, BlockPos localPos, Direction localFace, Vec3 localHit,
        InteractionHand hand) implements CustomPacketPayload {
    public static final Type<ContraptionUseC2SPacket> TYPE = new Type<>(ZPSMod.resource("contraption_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionUseC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionUseC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            BlockPos motorPos = buffer.readBlockPos();
            BlockPos localPos = buffer.readBlockPos();
            Direction localFace = buffer.readEnum(Direction.class);
            Vec3 localHit = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            return new ContraptionUseC2SPacket(motorPos, localPos, localFace, localHit, hand);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionUseC2SPacket packet) {
            buffer.writeBlockPos(packet.motorPos);
            buffer.writeBlockPos(packet.localPos);
            buffer.writeEnum(packet.localFace);
            buffer.writeDouble(packet.localHit.x);
            buffer.writeDouble(packet.localHit.y);
            buffer.writeDouble(packet.localHit.z);
            buffer.writeEnum(packet.hand);
        }
    };

    public static void handle(ContraptionUseC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.motorPos) instanceof ServoMotorBlockEntity motor)) return;
            motor.useContraptionBlock(packet.localPos, packet.localFace, packet.localHit, sender, packet.hand);
        });
    }

    @Override
    public Type<ContraptionUseC2SPacket> type() {
        return TYPE;
    }
}
