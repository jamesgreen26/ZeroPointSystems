package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when a player finishes mining a block off a contraption. */
public record ContraptionBreakC2SPacket(BlockPos motorPos, BlockPos localPos) implements CustomPacketPayload {
    public static final Type<ContraptionBreakC2SPacket> TYPE = new Type<>(ZPSMod.resource("contraption_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionBreakC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ContraptionBreakC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ContraptionBreakC2SPacket(buffer.readBlockPos(), buffer.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ContraptionBreakC2SPacket packet) {
            buffer.writeBlockPos(packet.motorPos);
            buffer.writeBlockPos(packet.localPos);
        }
    };

    public static void handle(ContraptionBreakC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.motorPos) instanceof ServoMotorBlockEntity motor)) return;
            motor.breakContraptionBlock(packet.localPos, sender);
        });
    }

    @Override
    public Type<ContraptionBreakC2SPacket> type() {
        return TYPE;
    }
}
