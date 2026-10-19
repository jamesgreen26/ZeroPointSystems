package g_mungus.zps.mixin.flywheel;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;
import g_mungus.zps.client.exhaust.ExhaustLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the exhaust layer take its materials' draws: Flywheel sets up GL state here for every draw
 * on both backends, and resets it once all are drawn. Optional: if these move, exhaust draws
 * nothing, since the layer composites only what reached it.
 */
@Mixin(value = MaterialRenderState.class, remap = false)
public abstract class MaterialRenderStateMixin {

    @Inject(method = "setup", at = @At("HEAD"), require = 0)
    private static void zps$beforeSetup(Material material, CallbackInfo ci) {
        ExhaustLayer.beforeSetup();
    }

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private static void zps$afterSetup(Material material, CallbackInfo ci) {
        ExhaustLayer.afterSetup(material);
    }

    /** Flywheel's OIT draws follow the plain ones with no reset between: an exhaust draw may be the last of those. */
    @Inject(method = "setupOit", at = @At("HEAD"), require = 0)
    private static void zps$beforeSetupOit(Material material, CallbackInfo ci) {
        ExhaustLayer.beforeSetup();
    }

    @Inject(method = "reset", at = @At("TAIL"), require = 0)
    private static void zps$afterReset(CallbackInfo ci) {
        ExhaustLayer.afterReset();
    }
}
