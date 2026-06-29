package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.ContraptionPath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a mining player as the crack stage of a contraption block changes
 * ({@code stage} 0-9, or -1 to clear). The server rebroadcasts it to nearby
 * players (except the sender) so they see the crack progress too.
 */
public record ContraptionBreakProgressC2SPacket(ContraptionPath path, BlockPos localPos, int stage)
        implements CustomPacketPayload {
    public static final Type<ContraptionBreakProgressC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("contraption_break_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionBreakProgressC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionBreakProgressC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ContraptionBreakProgressC2SPacket(ContraptionPath.STREAM_CODEC.decode(buffer),
                    buffer.readBlockPos(), buffer.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionBreakProgressC2SPacket packet) {
            ContraptionPath.STREAM_CODEC.encode(buffer, packet.path);
            buffer.writeBlockPos(packet.localPos);
            buffer.writeVarInt(packet.stage);
        }
    };

    private static final double BROADCAST_RANGE_SQR = 64.0 * 64.0;

    public static void handle(ContraptionBreakProgressC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (packet.path.resolve(level) == null) return;

            Vec3 motorCenter = Vec3.atCenterOf(packet.path.rootMotorPos());
            ContraptionDestroyStageS2CPacket s2c =
                    new ContraptionDestroyStageS2CPacket(packet.path, packet.localPos, sender.getId(), packet.stage);
            for (ServerPlayer player : level.players()) {
                if (player == sender) continue;
                if (player.position().distanceToSqr(motorCenter) > BROADCAST_RANGE_SQR) continue;
                PacketDistributor.sendToPlayer(player, s2c);
            }
        });
    }

    @Override
    public Type<ContraptionBreakProgressC2SPacket> type() {
        return TYPE;
    }
}
