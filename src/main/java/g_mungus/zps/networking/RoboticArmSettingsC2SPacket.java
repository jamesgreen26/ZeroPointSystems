package g_mungus.zps.networking;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RoboticArmSettingsC2SPacket(BlockPos blockPos, int retrieveAmount, boolean viewRange) {
    public static void encode(RoboticArmSettingsC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeVarInt(packet.retrieveAmount);
        buffer.writeBoolean(packet.viewRange);
    }

    public static RoboticArmSettingsC2SPacket decode(FriendlyByteBuf buffer) {
        return new RoboticArmSettingsC2SPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(RoboticArmSettingsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof RoboticArmBlockEntity be)) return;
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) return;
            be.setArmSettings(packet.retrieveAmount, packet.viewRange);
        });
        context.setPacketHandled(true);
    }
}
