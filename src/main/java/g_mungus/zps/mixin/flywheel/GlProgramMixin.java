package g_mungus.zps.mixin.flywheel;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import g_mungus.zps.client.exhaust.ExhaustParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands Flywheel's programs the exhaust batch ring each time one is bound. Flywheel has no way to
 * give an instance shader a texture of its own, and binding here covers both backends and every
 * OIT pass. Optional: if the method moves, only exhaust stops drawing properly.
 */
@Mixin(value = GlProgram.class, remap = false)
public abstract class GlProgramMixin {

    @Inject(method = "bind", at = @At("TAIL"), require = 0)
    private void zps$bindExhaustBatches(CallbackInfo ci) {
        ExhaustParticles.bindTo((GlProgram) (Object) this);
    }
}
