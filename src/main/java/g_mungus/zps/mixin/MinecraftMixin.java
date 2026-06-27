package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import g_mungus.zps.client.ContraptionInteractionClient;
import net.minecraft.client.Minecraft;

/**
 * Routes left/right click input to the contraption interaction handler when the
 * player is aiming at a contraption block (which lives in local space and is air
 * in the world, so vanilla's pick pipeline ignores it). When handled, vanilla's
 * world interaction is cancelled.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void zps$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (ContraptionInteractionClient.handleStartAttack())
			cir.setReturnValue(true);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void zps$continueAttack(boolean leftDown, CallbackInfo ci) {
		if (ContraptionInteractionClient.handleContinueAttack(leftDown))
			ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void zps$startUseItem(CallbackInfo ci) {
		if (ContraptionInteractionClient.handleStartUseItem())
			ci.cancel();
	}

	@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
	private void zps$pickBlock(CallbackInfo ci) {
		if (ContraptionInteractionClient.handlePickBlock())
			ci.cancel();
	}
}
