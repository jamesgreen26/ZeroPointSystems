package g_mungus.zps.mixin;

import g_mungus.zps.client.screens.ControllerClientHooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneak and sprint are toggle mappings, whose {@code isDown} replaces {@link KeyMapping#isDown}
 * outright rather than calling it, so {@link KeyMappingMixin} alone never shadows them. Without
 * this a sneak key bound to a controller's channel would also reach the server, and step the rider
 * off.
 */
@Mixin(ToggleKeyMapping.class)
public class ToggleKeyMappingMixin {

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void zps$shadowIsDown(CallbackInfoReturnable<Boolean> cir) {
        KeyMapping mapping = (KeyMapping) (Object) this;
        if (ControllerClientHooks.isShadowed(mapping, mapping.getKey())) {
            cir.setReturnValue(false);
        }
    }
}
