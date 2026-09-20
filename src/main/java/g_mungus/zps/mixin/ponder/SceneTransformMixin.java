package g_mungus.zps.mixin.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.client.ponder.api.SceneViewOffset;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a scene slide its view sideways, which Ponder has no way to do: it always keeps the middle
 * of the base plate in the middle of the screen. The shift goes on the end of the scene transform,
 * so everything drawn through it moves as one, the plate's shadow included, and so do the text
 * boxes, whose anchors are worked out through the same transform.
 *
 * <p>Going the other way, from the screen to the scene, Ponder does not invert that transform but
 * walks back through its steps by hand, so the shift is undone by hand there too. That is what
 * keeps the block under the mouse the right one in identify mode while the view is shifted.
 */
@Mixin(value = PonderScene.SceneTransform.class, remap = false)
public class SceneTransformMixin implements SceneViewOffset {

    @Unique
    private float zps$offsetX, zps$offsetY, zps$offsetZ;

    @Override
    public void zps$setViewOffset(float x, float y, float z) {
        zps$offsetX = x;
        zps$offsetY = y;
        zps$offsetZ = z;
    }

    @Inject(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;",
            at = @At("RETURN"), remap = false)
    private void zps$applyViewOffset(PoseStack ms, float pt, CallbackInfoReturnable<PoseStack> cir) {
        if (zps$offsetX != 0f || zps$offsetY != 0f || zps$offsetZ != 0f) {
            ms.translate(zps$offsetX, zps$offsetY, zps$offsetZ);
        }
    }

    /** The shift is the last thing applied going to the screen, so the last thing undone coming back. */
    @Inject(method = "screenToScene(DDIF)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"),
            cancellable = true, remap = false)
    private void zps$undoViewOffset(double x, double y, int depth, float pt, CallbackInfoReturnable<Vec3> cir) {
        if (zps$offsetX != 0f || zps$offsetY != 0f || zps$offsetZ != 0f) {
            cir.setReturnValue(cir.getReturnValue().subtract(zps$offsetX, zps$offsetY, zps$offsetZ));
        }
    }
}
