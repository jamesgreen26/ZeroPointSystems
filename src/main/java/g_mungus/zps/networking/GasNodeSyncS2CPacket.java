package g_mungus.zps.networking;

import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Pushes a gas node's simulated state to nearby clients, for particles and HUD readouts.
 *
 * <p>Kelvin has a sync layer of its own, but it syncs whole chunks of node data on its own
 * schedule; this carries just the three numbers a block needs to draw itself, keyed to one block.
 */
public record GasNodeSyncS2CPacket(BlockPos blockPos, double gasMass, double pressure, double temperature) {

    public static void encode(GasNodeSyncS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos());
        buffer.writeDouble(packet.gasMass());
        buffer.writeDouble(packet.pressure());
        buffer.writeDouble(packet.temperature());
    }

    public static GasNodeSyncS2CPacket decode(FriendlyByteBuf buffer) {
        return new GasNodeSyncS2CPacket(buffer.readBlockPos(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(GasNodeSyncS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || !minecraft.level.isLoaded(packet.blockPos())) {
                return;
            }
            BlockEntity blockEntity = minecraft.level.getBlockEntity(packet.blockPos());
            if (blockEntity instanceof GasNodeBlockEntity node) {
                node.acceptSyncedState(packet.gasMass(), packet.pressure(), packet.temperature());
            }
        });
        context.setPacketHandled(true);
    }
}
