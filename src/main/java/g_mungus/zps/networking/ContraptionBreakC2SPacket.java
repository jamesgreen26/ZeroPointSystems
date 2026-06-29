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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when a player finishes mining a block off a contraption (possibly nested). */
public record ContraptionBreakC2SPacket(ContraptionPath path, BlockPos localPos) implements CustomPacketPayload {
    public static final Type<ContraptionBreakC2SPacket> TYPE = new Type<>(ZPSMod.resource("contraption_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionBreakC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionBreakC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ContraptionBreakC2SPacket(ContraptionPath.STREAM_CODEC.decode(buffer), buffer.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionBreakC2SPacket packet) {
            ContraptionPath.STREAM_CODEC.encode(buffer, packet.path);
            buffer.writeBlockPos(packet.localPos);
        }
    };

    public static void handle(ContraptionBreakC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            ServoMotorBlockEntity motor = packet.path.resolve(level);
            if (motor == null) return;
            motor.breakContraptionBlock(packet.localPos, sender);
        });
    }

    @Override
    public Type<ContraptionBreakC2SPacket> type() {
        return TYPE;
    }
}
