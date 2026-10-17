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
 * schedule; this carries just the few numbers a block needs to draw itself, keyed to one block:
 * its mass, pressure and temperature, and for a block that lets gas out, how its gas floats in what
 * it goes into, face by face.
 */
public record GasNodeSyncS2CPacket(BlockPos blockPos, double gasMass, double pressure,
                                   double temperature, float[] buoyancy) implements CustomPacketPayload {

    public static final Type<GasNodeSyncS2CPacket> TYPE = new Type<>(ZPSMod.resource("gas_node_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasNodeSyncS2CPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GasNodeSyncS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                    BlockPos pos = buffer.readBlockPos();
                    double mass = buffer.readDouble();
                    double pressure = buffer.readDouble();
                    double temperature = buffer.readDouble();
                    float[] buoyancy = new float[buffer.readVarInt()];
                    for (int i = 0; i < buoyancy.length; i++) {
                        buoyancy[i] = buffer.readFloat();
                    }
                    return new GasNodeSyncS2CPacket(pos, mass, pressure, temperature, buoyancy);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GasNodeSyncS2CPacket packet) {
                    buffer.writeBlockPos(packet.blockPos());
                    buffer.writeDouble(packet.gasMass());
                    buffer.writeDouble(packet.pressure());
                    buffer.writeDouble(packet.temperature());
                    buffer.writeVarInt(packet.buoyancy().length);
                    for (float buoyancy : packet.buoyancy()) {
                        buffer.writeFloat(buoyancy);
                    }
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
                node.acceptSyncedBuoyancy(packet.buoyancy());
            }
        });
    }

    @Override
    public Type<GasNodeSyncS2CPacket> type() {
        return TYPE;
    }
}
