package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.ContraptionInteractionClient;
import g_mungus.zps.contraption.ContraptionPath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Broadcast to nearby players so they render the crack overlay another player is
 * making on a contraption block (possibly nested). {@code breakerId} is the mining player's entity
 * id (vanilla semantics, so concurrent breakers don't clobber each other);
 * {@code stage} is 0-9, or -1 to clear.
 */
public record ContraptionDestroyStageS2CPacket(ContraptionPath path, BlockPos localPos, int breakerId, int stage)
        implements CustomPacketPayload {
    public static final Type<ContraptionDestroyStageS2CPacket> TYPE =
            new Type<>(ZPSMod.resource("contraption_destroy_stage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionDestroyStageS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionDestroyStageS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ContraptionDestroyStageS2CPacket(ContraptionPath.STREAM_CODEC.decode(buffer),
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionDestroyStageS2CPacket packet) {
            ContraptionPath.STREAM_CODEC.encode(buffer, packet.path);
            buffer.writeBlockPos(packet.localPos);
            buffer.writeVarInt(packet.breakerId);
            buffer.writeVarInt(packet.stage);
        }
    };

    public static void handle(ContraptionDestroyStageS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                ContraptionInteractionClient.onRemoteDestroyStage(packet.path, packet.localPos, packet.breakerId, packet.stage));
    }

    @Override
    public Type<ContraptionDestroyStageS2CPacket> type() {
        return TYPE;
    }
}
