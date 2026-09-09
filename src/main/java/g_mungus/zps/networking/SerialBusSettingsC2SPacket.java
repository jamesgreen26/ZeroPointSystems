package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the serial bus's screen sends whenever its mode changes. */
public record SerialBusSettingsC2SPacket(BlockPos blockPos, SerialBusMode mode) implements CustomPacketPayload {
    public static final Type<SerialBusSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("serial_bus_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SerialBusSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SerialBusSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new SerialBusSettingsC2SPacket(buffer.readBlockPos(), buffer.readEnum(SerialBusMode.class));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SerialBusSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    buffer.writeEnum(packet.mode);
                }
            };

    public static void handle(SerialBusSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof SerialBusBlockEntity bus)) {
                return;
            }
            // Ship-aware, so a bus riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            bus.setMode(packet.mode);
        });
    }

    @Override
    public Type<SerialBusSettingsC2SPacket> type() {
        return TYPE;
    }
}
