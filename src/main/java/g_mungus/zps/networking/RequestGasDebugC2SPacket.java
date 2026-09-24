package g_mungus.zps.networking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkEvent;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Asks the server for the pressure at every gas node near the player, for the debug overlay.
 *
 * <p>Ducts do not tick, so they never sync themselves; rather than tick every duct just to make a
 * debug view possible, the client asks for a snapshot while the overlay is switched on.
 */
public record RequestGasDebugC2SPacket(BlockPos center, int radius) {

    /** Bounds the work a client can ask the server to do. */
    private static final int MAX_RADIUS = 64;
    private static final int MAX_SAMPLES = 2048;

    public static void encode(RequestGasDebugC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.center());
        buffer.writeVarInt(packet.radius());
    }

    public static RequestGasDebugC2SPacket decode(FriendlyByteBuf buffer) {
        return new RequestGasDebugC2SPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(RequestGasDebugC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.setPacketHandled(true);
        // The overlay is a development tool; a released server answers nothing.
        if (FMLLoader.isProduction()) {
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }

            int radius = Math.min(packet.radius(), MAX_RADIUS);
            BlockPos center = packet.center();
            if (!center.closerThan(player.blockPosition(), MAX_RADIUS * 2.0)) {
                return;
            }

            DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
            HashSet<DuctNodePos> nodes =
                    kelvin.getNodesInDimension().get(level.dimension().location());
            if (nodes == null) {
                return;
            }

            List<GasDebugS2CPacket.Sample> samples = new ArrayList<>();
            for (DuctNodePos node : nodes) {
                if (samples.size() >= MAX_SAMPLES) {
                    break;
                }
                // Kelvin's dump() on server stop clears its nodes but leaves this index
                // populated, so positions here can outlive the nodes they name.
                if (kelvin.getNodeAt(node) == null) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(node.getX(), node.getY(), node.getZ());
                if (!pos.closerThan(center, radius)) {
                    continue;
                }
                samples.add(new GasDebugS2CPacket.Sample(pos, (float) kelvin.getPressureAt(node)));
            }

            ZPSGamePackets.sendToPlayer(player, new GasDebugS2CPacket(samples));
        });
    }
}
