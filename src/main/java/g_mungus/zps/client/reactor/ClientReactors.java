package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.networking.ReactorShapeS2CPacket;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Every reactor the client has been told about, and the Flywheel effects that draw them.
 *
 * <p>Only the render thread touches the map: packet handlers run through {@code enqueueWork} and
 * the events below are posted from it. Flywheel's own threads only ever see the immutable
 * {@link ClientReactor} objects.
 *
 * <p>Flywheel throws its visualization manager away on backend changes, F3+A, resource reloads,
 * and render-distance changes. Block entities re-register themselves through chunk rebuilds;
 * effects do not. So every tick this compares the current manager to the one the effects were
 * last queued into and re-queues them all when it has changed.
 */
public final class ClientReactors {

    private static final Int2ObjectMap<ClientReactor> REACTORS = new Int2ObjectOpenHashMap<>();
    private static @Nullable VisualizationManager lastManager;
    private static boolean lastEnabled;

    private ClientReactors() {
    }

    public static void accept(ReactorShapeS2CPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (packet.shape().isEmpty() || !CavityShapes.isSupported(packet.shape())) {
            ZPSMod.LOGGER.debug("Dropping reactor {}: unusable shape", packet.id());
            return;
        }

        remove(packet.id());
        ClientReactor reactor = new ClientReactor(level, packet.id(), packet.shape(), packet.heat());
        REACTORS.put(packet.id(), reactor);
        if (enabled()) {
            queueAdd(reactor);
        }
    }

    public static void setHeat(int id, float heat) {
        ClientReactor reactor = REACTORS.get(id);
        if (reactor != null) {
            reactor.setTargetHeat(heat);
        }
    }

    public static void remove(int id) {
        ClientReactor reactor = REACTORS.remove(id);
        if (reactor != null) {
            VisualizationHelper.queueRemove(reactor.effect());
        }
    }

    public static void clearAll() {
        for (ClientReactor reactor : new ArrayList<>(REACTORS.values())) {
            VisualizationHelper.queueRemove(reactor.effect());
        }
        REACTORS.clear();
        lastManager = null;
    }

    /**
     * Hands the effect to Flywheel, noting whether it will build its visual for a grid or for the
     * ground. The visual cannot change its mind once built, so {@link #onRenderFrame} watches for
     * the reactor's grid state moving away from what was queued.
     */
    private static void queueAdd(ClientReactor reactor) {
        reactor.setQueuedOnGrid(reactor.isOnGrid());
        VisualizationHelper.queueAdd(reactor.effect());
    }

    /** Reactors known to the client, for debugging. */
    public static int count() {
        return REACTORS.size();
    }

    private static boolean enabled() {
        return ZPSConfig.showReactorGlow();
    }

    // --- events -------------------------------------------------------------------------------

    /**
     * The host chunk's blocks have arrived, or arrived again. The server sends the shape to whoever
     * is tracking the chunk, not whoever has it, so the shape can come first; the visual reads the
     * walls when it is built, so one already drawn is built over.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() || REACTORS.isEmpty() || !enabled()) {
            return;
        }
        ChunkPos loaded = event.getChunk().getPos();
        for (ClientReactor reactor : REACTORS.values()) {
            if (reactor.hostChunk().equals(loaded)) {
                VisualizationHelper.queueRemove(reactor.effect());
                queueAdd(reactor);
            }
        }
    }

    /** A chunk left the client: any reactor hosted in it is out of range now. */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide() || REACTORS.isEmpty()) {
            return;
        }
        ChunkPos unloaded = event.getChunk().getPos();
        for (ClientReactor reactor : new ArrayList<>(REACTORS.values())) {
            if (reactor.hostChunk().equals(unloaded)) {
                remove(reactor.id());
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            clearAll();
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    /**
     * Snapshot every reactor's grid transform for this frame. Runs before the level render, so
     * before Flywheel's frame plan reads it from worker threads.
     *
     * <p>A reactor whose grid has arrived or gone since its effect was queued gets its visual
     * rebuilt: on a rejoin the reactor packet can land before Sable has the sublevel it sits on,
     * and a visual built for the ground would leave the glow at the sublevel's far-off plot.
     */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        boolean enabled = enabled();
        for (ClientReactor reactor : REACTORS.values()) {
            reactor.updateRenderTransform();
            if (enabled && reactor.isOnGrid() != reactor.queuedOnGrid()) {
                VisualizationHelper.queueRemove(reactor.effect());
                queueAdd(reactor);
            }
        }
    }

    /** Re-queue every effect whenever Flywheel has replaced its manager, or the toggle flipped. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (REACTORS.isEmpty()) {
            return;
        }
        VisualizationManager manager = VisualizationManager.get(Minecraft.getInstance().level);
        boolean enabled = enabled();
        if (manager == lastManager && enabled == lastEnabled) {
            return;
        }
        if (manager != null && manager == lastManager && !enabled) {
            // Toggled off on a live manager: take the effects down.
            for (ClientReactor reactor : REACTORS.values()) {
                manager.effects().queueRemove(reactor.effect());
            }
        } else if (manager != null && enabled) {
            for (ClientReactor reactor : REACTORS.values()) {
                queueAdd(reactor);
            }
        }
        lastManager = manager;
        lastEnabled = enabled;
    }
}
