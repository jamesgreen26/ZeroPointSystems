package g_mungus.zps.client.reactor;

import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Glows with no reactor behind them: a shape, a place and a heat, drawn in the level through
 * {@link ReactorGlowRenderer} like any reactor's. Nothing on the server knows about
 * them and nothing has to be built; they are for looking at the effect, and they are the worked
 * example of drawing it artificially, which is all a ponder scene will need to do with its own
 * matrices in place of the level's.
 *
 * <p>Render thread only.
 */
public final class ReactorGlowPreviews {

    private record Preview(ReactorGlowMesh mesh, BlockPos origin, AABB bounds, float heat, float seed) {
    }

    private static final List<Preview> PREVIEWS = new ArrayList<>();

    private ReactorGlowPreviews() {
    }

    /**
     * Adds the glow of a box cavity whose lowest corner is {@code origin}. The walls are taken as
     * flat, whatever blocks happen to be there.
     *
     * @return false if the size is unusable
     */
    public static boolean addBox(BlockPos origin, int sizeX, int sizeY, int sizeZ, float heat) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            return false;
        }
        LongSet cells = new LongOpenHashSet();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(sizeX - 1, sizeY - 1, sizeZ - 1))) {
            cells.add(pos.asLong());
        }
        return add(CavityShapes.fromCells(cells), heat);
    }

    /** Adds the glow of any cavity shape, in world coordinates as the server would send it. */
    public static boolean add(VoxelShape shape, float heat) {
        ReactorGlowMesh mesh = ReactorGlowMesh.build(shape, null);
        if (mesh == null) {
            return false;
        }
        PREVIEWS.add(new Preview(mesh, CavityShapes.origin(shape), shape.bounds(), heat, PREVIEWS.size() * 0.37f % 1f));
        return true;
    }

    public static int clear() {
        int count = PREVIEWS.size();
        PREVIEWS.forEach(preview -> preview.mesh().close());
        PREVIEWS.clear();
        return count;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || PREVIEWS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float seconds = ReactorGlowRenderer.seconds(level, event.getPartialTick());
        Matrix4f modelView = new Matrix4f();
        for (Preview preview : PREVIEWS) {
            if (!event.getFrustum().isVisible(preview.bounds())) {
                continue;
            }
            BlockPos origin = preview.origin();
            modelView.set(event.getPoseStack().last().pose()).translate(
                    (float) (origin.getX() - camera.x), (float) (origin.getY() - camera.y), (float) (origin.getZ() - camera.z));
            ReactorGlowRenderer.draw(preview.mesh(), modelView, event.getProjectionMatrix(),
                    preview.heat(), preview.seed(), seconds, true);
        }
    }
}
