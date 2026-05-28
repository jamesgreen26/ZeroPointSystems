package g_mungus.zps.networking;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
            if (context.getSender() == null || context.getSender().level() == null) return;
            if (!(context.getSender().level().getBlockEntity(packet.blockPos) instanceof RoboticArmBlockEntity be)) return;
            if (!packet.blockPos.closerToCenterThan(context.getSender().position(), 8.0D)) return;
            be.setArmSettings(packet.retrieveAmount, packet.viewRange);
        });
        context.setPacketHandled(true);
    }
}
