package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Asks the server for the pressure at every gas node near the player, for the debug overlay.
 *
 * <p>Ducts do not tick, so they never sync themselves; rather than tick every duct just to make a
 * debug view possible, the client asks for a snapshot while the overlay is switched on.
 */
public record RequestGasDebugC2SPacket(BlockPos center, int radius) implements CustomPacketPayload {

    public static final Type<RequestGasDebugC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("request_gas_debug"));

    /** Bounds the work a client can ask the server to do. */
    private static final int MAX_RADIUS = 64;
    private static final int MAX_SAMPLES = 2048;

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGasDebugC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestGasDebugC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new RequestGasDebugC2SPacket(buffer.readBlockPos(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestGasDebugC2SPacket packet) {
                    buffer.writeBlockPos(packet.center());
                    buffer.writeVarInt(packet.radius());
                }
            };

    public static void handle(RequestGasDebugC2SPacket packet, IPayloadContext context) {
        // The overlay is a development tool; a released server answers nothing.
        if (FMLLoader.isProduction()) {
            return;
        }
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
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

            PacketDistributor.sendToPlayer(player, new GasDebugS2CPacket(samples));
        });
    }

    @Override
    public Type<RequestGasDebugC2SPacket> type() {
        return TYPE;
    }
}
