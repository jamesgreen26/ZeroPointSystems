package g_mungus.zps.client.ponder.api;

import com.mojang.blaze3d.systems.RenderSystem;
import g_mungus.zps.client.reactor.ReactorGlowMesh;
import g_mungus.zps.client.reactor.ReactorGlowRenderer;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The reactor glow inside a ponder scene. Nothing in a ponder level runs a reactor, so the scene
 * says what the heat is and this draws it, through the same {@link ReactorGlowRenderer} the level
 * uses. {@link #heatTo} is the whole of the control: a target and how long to take getting there.
 *
 * <p>Ponder batches each chunk layer and flushes it when the next one starts. The glow has to land
 * after the opaque walls and before the glass, the same as in the level, so it draws at the start
 * of the translucent layer, having first flushed whatever opaque geometry is still waiting. The
 * glass, waiting or not yet submitted, is flushed after it.
 *
 * <p>The mesh is cut from the ponder level's walls the first time it is drawn, so the scene wants
 * its real wall blocks in place by then.
 */
public class ReactorGlowElement extends AnimatedSceneElementBase {

    /** Every element holding a mesh, so they can all be let go of when the GL objects must go. */
    private static final Set<ReactorGlowElement> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private static final float VISIBLE_HEAT = 0.02f;

    private final VoxelShape shape;
    private final BlockPos origin;
    private final float seed;
    private @Nullable ReactorGlowMesh mesh;

    private float from;
    private float to;
    private int duration;
    private int elapsed;
    /** How far the heat wanders either side of its value, for a reactor that is struggling. */
    private float flicker;
    private int ticks;

    /** A glow filling the box of cells between two corners, both inclusive, in scene coordinates. */
    public ReactorGlowElement(BlockPos min, BlockPos max, float seed) {
        LongSet cells = new LongOpenHashSet();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            cells.add(pos.asLong());
        }
        this.shape = CavityShapes.fromCells(cells);
        this.origin = CavityShapes.origin(shape);
        this.seed = seed;
        forceApplyFade(1);
    }

    /** Eases the heat, which is chamber temperature over ignition temperature, to a new value. */
    public void heatTo(float heat, int ticks) {
        from = heat(0);
        to = heat;
        duration = Math.max(0, ticks);
        elapsed = 0;
        if (duration == 0) {
            from = heat;
        }
    }

    public void setFlicker(float amplitude) {
        flicker = amplitude;
    }

    private float heat(float partialTicks) {
        if (duration <= 0) {
            return to;
        }
        float t = Mth.clamp((elapsed + partialTicks) / duration, 0f, 1f);
        return Mth.lerp(t * t * (3f - 2f * t), from, to);
    }

    @Override
    public void tick(@NotNull PonderScene scene) {
        super.tick(scene);
        ticks++;
        if (elapsed < duration) {
            elapsed++;
        }
    }

    @Override
    public void reset(@NotNull PonderScene scene) {
        super.reset(scene);
        forceApplyFade(1);
        from = to = 0f;
        duration = elapsed = ticks = 0;
        flicker = 0f;
        release();
    }

    private void release() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        LIVE.remove(this);
    }

    /** Frees every ponder glow's mesh; each is built again the next time it is drawn. */
    public static void releaseAll() {
        for (ReactorGlowElement element : Set.copyOf(LIVE)) {
            element.release();
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        releaseAll();
    }

    @Override
    protected void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics,
                               float fade, float pt) {
        if (type != RenderType.translucent()) {
            return;
        }
        float seconds = (ticks + pt) / 20f;
        float heat = heat(pt);
        if (flicker > 0f) {
            heat += flicker * 0.5f * (Mth.sin(seconds * 9.1f) + Mth.sin(seconds * 5.3f + 1f));
        }
        if (heat < VISIBLE_HEAT) {
            return;
        }
        if (mesh == null) {
            mesh = ReactorGlowMesh.build(shape, world, false);
            if (mesh == null) {
                return;
            }
            LIVE.add(this);
        }

        // The opaque layers still waiting in the buffer have to reach the screen, and the depth
        // buffer, before the glow is tested against them.
        if (buffer instanceof SuperRenderTypeBuffer batched) {
            batched.draw(RenderType.solid());
            batched.draw(RenderType.cutoutMipped());
            batched.draw(RenderType.cutout());
        }

        // Gui rendering keeps its transforms on the pose stack, with the render system's
        // model-view underneath. The projection is orthographic, which the renderer handles.
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(graphics.pose().last().pose())
                .translate(origin.getX(), origin.getY(), origin.getZ());
        ReactorGlowRenderer.draw(mesh, modelView, RenderSystem.getProjectionMatrix(), heat, seed, seconds, false);
    }
}
