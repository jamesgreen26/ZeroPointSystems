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

/** What the serial bus's screen sends when the player accepts its settings. */
public record SerialBusSettingsC2SPacket(BlockPos blockPos, SerialBusMode mode, String expression)
        implements CustomPacketPayload {
    public static final Type<SerialBusSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("serial_bus_settings"));

    /** As much of a getter to mapper chain as the screen will take, and as the wire will carry. */
    public static final int MAX_EXPRESSION_LENGTH = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, SerialBusSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SerialBusSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new SerialBusSettingsC2SPacket(
                            buffer.readBlockPos(),
                            buffer.readEnum(SerialBusMode.class),
                            buffer.readUtf(MAX_EXPRESSION_LENGTH));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SerialBusSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    buffer.writeEnum(packet.mode);
                    buffer.writeUtf(packet.expression, MAX_EXPRESSION_LENGTH);
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
            // Mode first: switching modes resets the record, which would otherwise throw away the
            // verdict belonging to the expression arriving in this same packet.
            bus.setMode(packet.mode);
            bus.setExpression(sanitise(packet.expression));
        });
    }

    /** A client can send anything; the bus wants one line of at most the length we advertise. */
    private static String sanitise(String expression) {
        String oneLine = expression.replace('\n', ' ').replace('\r', ' ').strip();
        return oneLine.length() > MAX_EXPRESSION_LENGTH
                ? oneLine.substring(0, MAX_EXPRESSION_LENGTH)
                : oneLine;
    }

    @Override
    public Type<SerialBusSettingsC2SPacket> type() {
        return TYPE;
    }
}
