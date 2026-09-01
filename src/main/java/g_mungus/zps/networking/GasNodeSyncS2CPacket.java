package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Pushes a gas node's simulated state to nearby clients, for particles and HUD readouts.
 *
 * <p>Kelvin has a sync layer of its own, but it syncs whole chunks of node data on its own
 * schedule; this carries just the three numbers a block needs to draw itself, keyed to one block.
 */
public record GasNodeSyncS2CPacket(BlockPos blockPos, double gasMass, double pressure,
                                   double temperature) implements CustomPacketPayload {

    public static final Type<GasNodeSyncS2CPacket> TYPE = new Type<>(ZPSMod.resource("gas_node_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasNodeSyncS2CPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GasNodeSyncS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new GasNodeSyncS2CPacket(buffer.readBlockPos(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GasNodeSyncS2CPacket packet) {
                    buffer.writeBlockPos(packet.blockPos());
                    buffer.writeDouble(packet.gasMass());
                    buffer.writeDouble(packet.pressure());
                    buffer.writeDouble(packet.temperature());
                }
            };

    public static void handle(GasNodeSyncS2CPacket packet, IPayloadContext context) {
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
    }

    @Override
    public Type<GasNodeSyncS2CPacket> type() {
        return TYPE;
    }
}
