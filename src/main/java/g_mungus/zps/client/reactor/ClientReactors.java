package g_mungus.zps.client.reactor;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.networking.ReactorShapeS2CPacket;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * Every reactor the client has been told about, and the level render hook that draws their glow
 * through {@link ReactorGlowRenderer}.
 *
 * <p>Only the render thread touches any of it: packet handlers run through {@code enqueueWork} and
 * the events below are posted from it.
 */
public final class ClientReactors {

    private static final Int2ObjectMap<ClientReactor> REACTORS = new Int2ObjectOpenHashMap<>();

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
        REACTORS.put(packet.id(), new ClientReactor(level, packet.id(), packet.shape(), packet.heat()));
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
            reactor.releaseGlowMesh();
        }
    }

    public static void clearAll() {
        REACTORS.values().forEach(ClientReactor::releaseGlowMesh);
        REACTORS.clear();
    }

    /**
     * Throws every built mesh away, to be built again on the next frame it is wanted. For a
     * resource reload, which re-bakes the wall models the meshes were cut from.
     */
    public static void invalidateMeshes() {
        REACTORS.values().forEach(ClientReactor::releaseGlowMesh);
    }

    /** Reactors known to the client, for debugging. */
    public static int count() {
        return REACTORS.size();
    }

    // --- events -------------------------------------------------------------------------------

    /**
     * The host chunk's blocks have arrived, or arrived again. The server sends the shape to whoever
     * is tracking the chunk, not whoever has it, so the shape can come first; the mesh reads the
     * walls when it is built, so one already built is thrown away to be built over.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() || REACTORS.isEmpty()) {
            return;
        }
        ChunkPos loaded = event.getChunk().getPos();
        for (ClientReactor reactor : REACTORS.values()) {
            if (reactor.hostChunk().equals(loaded)) {
                reactor.releaseGlowMesh();
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

    /** Ease every reactor's heat toward what the server last said. */
    public static void onClientTick(ClientTickEvent.Post event) {
        for (ClientReactor reactor : REACTORS.values()) {
            reactor.tickHeat();
        }
    }

    /**
     * Draws every reactor's glow, one draw each.
     *
     * <p>After block entities and so before translucent blocks, which write depth: drawn any later
     * the glow would fail the depth test against the very glass it is meant to be seen through.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || REACTORS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (!ZPSConfig.showReactorGlow()) {
            invalidateMeshes();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float seconds = ReactorGlowRenderer.seconds(level, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Matrix4d grid = new Matrix4d();
        Matrix4d local = new Matrix4d();
        Matrix4f localF = new Matrix4f();
        Matrix4f modelView = new Matrix4f();
        for (ClientReactor reactor : REACTORS.values()) {
            float heat = reactor.displayHeat();
            if (heat < ClientReactor.VISIBLE_HEAT) {
                continue;
            }
            // A reactor on a moving grid has its blocks in a far-off region of the level and is
            // drawn wherever the grid is this frame; its shape's bounds say nothing of where that is.
            boolean onGrid = reactor.gridTransform(grid) != null;
            if (!onGrid && !event.getFrustum().isVisible(reactor.shape().bounds())) {
                continue;
            }
            ReactorGlowMesh mesh = reactor.glowMesh(level);
            if (mesh == null) {
                continue;
            }

            // The mesh's frame to the camera's, in doubles while the numbers are world-sized: out
            // from the cavity's corner, through the grid if there is one, back by the camera.
            BlockPos origin = reactor.origin();
            local.translation(-camera.x, -camera.y, -camera.z);
            if (onGrid) {
                local.mul(grid);
            }
            local.translate(origin.getX(), origin.getY(), origin.getZ());
            modelView.set(event.getModelViewMatrix()).mul(localF.set(local));

            ReactorGlowRenderer.draw(mesh, modelView, event.getProjectionMatrix(), heat, reactor.seed(), seconds, true);
        }
    }
}
