package g_mungus.zps.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import g_mungus.zps.client.screens.ControllerClientHooks;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shadows key mappings whose key is driving a controller the player is riding: they read as
 * released, and clicks made on them are dropped rather than held until the ride ends.
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    private InputConstants.Key key;

    @Shadow
    private int clickCount;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void zps$shadowIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (ControllerClientHooks.isShadowed((KeyMapping) (Object) this, key)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void zps$shadowConsumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (ControllerClientHooks.isShadowed((KeyMapping) (Object) this, key)) {
            clickCount = 0;
            cir.setReturnValue(false);
        }
    }
}
