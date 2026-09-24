package g_mungus.zps.client.ponder.api;

import g_mungus.zps.mixin.ponder.PonderSceneAccessor;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a ponder scene's view while it plays: zooms it, and slides it sideways. Ponder has the
 * scale as a plain number on the scene, read every frame but only ever set by
 * {@code scaleSceneView} while the scene is built, and has no sideways shift at all; this sets the
 * one through {@link PonderSceneAccessor} and the other through {@link SceneViewOffset}.
 *
 * <p>It draws nothing. It is an element only so that it is called every frame, where it eases
 * both with the partial tick and the move comes out smooth, and so that it is reset with the
 * scene, where it puts the scale back for the next playthrough. The shift needs no putting back:
 * Ponder makes a new transform whenever a scene begins.
 *
 * <p>The scale is applied about the middle of the base plate, so whatever is off that axis drifts
 * outward as the scene grows; the shift is what brings it back to the middle of the screen.
 */
public class SceneViewElement extends AnimatedSceneElementBase {

    private @Nullable PonderScene scene;
    /** The scale the scene was built with, which {@link #reset} returns it to. */
    private float baseScale;
    private float fromScale, toScale;
    private Vec3 fromOffset = Vec3.ZERO, toOffset = Vec3.ZERO;
    private int duration;
    private int elapsed;

    public SceneViewElement() {
        forceApplyFade(1);
    }

    /**
     * Eases the view to a zoom and a shift. Adds itself to the scene the first time, so this is
     * all a storyboard has to call.
     *
     * @param zoom   a multiple of the scale the scene was built with: 1 is where it started
     * @param offset how far to slide everything, in blocks along the scene's own axes, which is
     *               not the screen's: what it looks like depends on where the camera is
     */
    public void moveTo(PonderScene scene, float zoom, Vec3 offset, int ticks) {
        if (this.scene == null) {
            this.scene = scene;
            baseScale = scene.getScaleFactor();
            toScale = baseScale;
            toOffset = Vec3.ZERO;
            scene.addElement(this);
        }
        fromScale = Mth.lerp(progress(0), fromScale, toScale);
        fromOffset = fromOffset.lerp(toOffset, progress(0));
        toScale = baseScale * zoom;
        toOffset = offset;
        duration = Math.max(0, ticks);
        elapsed = 0;
        if (duration == 0) {
            apply(0);
        }
    }

    /** How far through the current move the view is, eased at both ends. */
    private float progress(float partialTicks) {
        if (duration <= 0) {
            return 1f;
        }
        float t = Mth.clamp((elapsed + partialTicks) / duration, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void tick(@NotNull PonderScene scene) {
        super.tick(scene);
        if (elapsed < duration) {
            elapsed++;
            apply(0);
        }
    }

    @Override
    public void reset(@NotNull PonderScene scene) {
        super.reset(scene);
        forceApplyFade(1);
        if (this.scene != null) {
            ((PonderSceneAccessor) this.scene).zps$setScaleFactor(baseScale);
            this.scene = null;
        }
        fromOffset = toOffset = Vec3.ZERO;
        duration = elapsed = 0;
    }

    @Override
    protected void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        if (elapsed < duration) {
            apply(pt);
        }
    }

    private void apply(float partialTicks) {
        if (scene == null) {
            return;
        }
        float t = progress(partialTicks);
        ((PonderSceneAccessor) scene).zps$setScaleFactor(Mth.lerp(t, fromScale, toScale));
        Vec3 offset = fromOffset.lerp(toOffset, t);
        ((SceneViewOffset) scene.getTransform()).zps$setViewOffset((float) offset.x, (float) offset.y, (float) offset.z);
    }
}
