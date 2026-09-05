package g_mungus.zps.reactor;

import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.networking.ReactorRemovedS2CPacket;
import g_mungus.zps.networking.ReactorShapeS2CPacket;
import g_mungus.zps.networking.ReactorStateS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the server tells clients about reactors, and when. Everything is keyed to the chunk the
 * host cell sits in: players tracking that chunk get the shape, the heat, and the removal.
 */
public final class ReactorSync {

    /** Ticks between heat updates. */
    public static final int STATE_SYNC_INTERVAL = 10;
    /** Heat changes smaller than this are not worth a packet. */
    public static final float HEAT_EPSILON = 1f / 64f;
    /** Heat is capped so a runaway chamber does not send silly numbers. */
    public static final float HEAT_MAX = 8f;

    private ReactorSync() {
    }

    /** Chamber temperature as a multiple of the ignition temperature, capped. */
    public static float heatOf(double temperatureK) {
        return (float) Math.min(temperatureK / ZPSConfig.reactorIgnitionTemperatureK(), HEAT_MAX);
    }

    public static ReactorShapeS2CPacket shapePacket(Reactor reactor, float heat) {
        return new ReactorShapeS2CPacket(reactor.id(), reactor.shape(), heat);
    }

    public static void sendShape(ServerLevel level, Reactor reactor, float heat) {
        PacketDistributor.sendToPlayersTrackingChunk(level, hostChunk(reactor), shapePacket(reactor, heat));
    }

    public static void sendShapeTo(ServerPlayer player, Reactor reactor, float heat) {
        PacketDistributor.sendToPlayer(player, shapePacket(reactor, heat));
    }

    public static void sendState(ServerLevel level, Reactor reactor, float heat) {
        PacketDistributor.sendToPlayersTrackingChunk(level, hostChunk(reactor), new ReactorStateS2CPacket(reactor.id(), heat));
    }

    public static void sendRemoved(ServerLevel level, Reactor reactor) {
        PacketDistributor.sendToPlayersTrackingChunk(level, hostChunk(reactor), new ReactorRemovedS2CPacket(reactor.id()));
    }

    public static ChunkPos hostChunk(Reactor reactor) {
        return new ChunkPos(reactor.host());
    }
}
