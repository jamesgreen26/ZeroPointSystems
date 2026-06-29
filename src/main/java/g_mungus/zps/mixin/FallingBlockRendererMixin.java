package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import g_mungus.zps.contraption.ContraptionRotatedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;

/**
 * Applies a falling block's inherited contraption rotation (see
 * {@link ContraptionRotatedEntity}) about the block's own centre, so a block detached
 * from a tilted/rotating contraption renders with that orientation as it falls. Wraps
 * vanilla's render with a balanced push/pop.
 */
@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

	@Inject(method = "render", at = @At("HEAD"))
	private void zps$pushRotation(FallingBlockEntity entity, float yaw, float partialTick, PoseStack pose,
		MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		pose.pushPose();
		if (entity instanceof ContraptionRotatedEntity rotated) {
			Quaternionf rotation = rotated.zps$getContraptionRotation();
			// Rotate about the block's own centre; identity (no inherited rotation) is a no-op.
			pose.translate(0.0, 0.5, 0.0);
			pose.mulPose(rotation);
			pose.translate(0.0, -0.5, 0.0);
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void zps$popRotation(FallingBlockEntity entity, float yaw, float partialTick, PoseStack pose,
		MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		pose.popPose();
	}
}
