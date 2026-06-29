package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.contraption.ContraptionScreenAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** {@code motorPos} is the host Servo Motor when the arm rides a contraption, else {@code null}. */
public record RoboticArmSettingsC2SPacket(BlockPos blockPos, @Nullable BlockPos motorPos, int retrieveAmount, boolean viewRange) implements CustomPacketPayload {
    public static final Type<RoboticArmSettingsC2SPacket> TYPE = new Type<>(ZPSMod.resource("robotic_arm_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoboticArmSettingsC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RoboticArmSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RoboticArmSettingsC2SPacket(buffer.readBlockPos(),
                    buffer.readBoolean() ? buffer.readBlockPos() : null, buffer.readVarInt(), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RoboticArmSettingsC2SPacket packet) {
            buffer.writeBlockPos(packet.blockPos);
            buffer.writeBoolean(packet.motorPos != null);
            if (packet.motorPos != null) {
                buffer.writeBlockPos(packet.motorPos);
            }
            buffer.writeVarInt(packet.retrieveAmount);
            buffer.writeBoolean(packet.viewRange);
        }
    };

    public static void handle(RoboticArmSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(ContraptionScreenAccess.resolve(level, packet.motorPos, packet.blockPos) instanceof RoboticArmBlockEntity be)) return;
            if (!ContraptionScreenAccess.inReach(sender, packet.motorPos, packet.blockPos)) return;
            be.setArmSettings(packet.retrieveAmount, packet.viewRange);
        });
    }

    @Override
    public Type<RoboticArmSettingsC2SPacket> type() {
        return TYPE;
    }
}
