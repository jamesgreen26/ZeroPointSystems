package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RoboticArmSettingsC2SPacket(BlockPos blockPos, int retrieveAmount, boolean viewRange) implements CustomPacketPayload {
    public static final Type<RoboticArmSettingsC2SPacket> TYPE = new Type<>(ZPSMod.resource("robotic_arm_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoboticArmSettingsC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RoboticArmSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RoboticArmSettingsC2SPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RoboticArmSettingsC2SPacket packet) {
            buffer.writeBlockPos(packet.blockPos);
            buffer.writeVarInt(packet.retrieveAmount);
            buffer.writeBoolean(packet.viewRange);
        }
    };

    public static void handle(RoboticArmSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof RoboticArmBlockEntity be)) return;
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) return;
            be.setArmSettings(packet.retrieveAmount, packet.viewRange);
        });
    }

    @Override
    public Type<RoboticArmSettingsC2SPacket> type() {
        return TYPE;
    }
}
