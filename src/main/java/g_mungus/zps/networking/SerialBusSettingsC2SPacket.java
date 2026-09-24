package g_mungus.zps.networking;

import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the serial bus's screen sends when the player accepts its settings. */
public record SerialBusSettingsC2SPacket(BlockPos blockPos, SerialBusMode mode, String expression) {

    /** As much of a getter to mapper chain as the screen will take, and as the wire will carry. */
    public static final int MAX_EXPRESSION_LENGTH = 256;

    public static void encode(SerialBusSettingsC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeEnum(packet.mode);
        buffer.writeUtf(packet.expression, MAX_EXPRESSION_LENGTH);
    }

    public static SerialBusSettingsC2SPacket decode(FriendlyByteBuf buffer) {
        return new SerialBusSettingsC2SPacket(
                buffer.readBlockPos(),
                buffer.readEnum(SerialBusMode.class),
                buffer.readUtf(MAX_EXPRESSION_LENGTH));
    }

    public static void handle(SerialBusSettingsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
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
        context.setPacketHandled(true);
    }

    /** A client can send anything; the bus wants one line of at most the length we advertise. */
    private static String sanitise(String expression) {
        String oneLine = expression.replace('\n', ' ').replace('\r', ' ').strip();
        return oneLine.length() > MAX_EXPRESSION_LENGTH
                ? oneLine.substring(0, MAX_EXPRESSION_LENGTH)
                : oneLine;
    }
}
