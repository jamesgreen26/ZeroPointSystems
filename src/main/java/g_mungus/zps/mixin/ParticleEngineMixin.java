package g_mungus.zps.mixin;

import g_mungus.zps.client.tractor.TractorBeamParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a running Tractor Beam drag particles. Hooked where the engine ticks each particle rather than in
 * {@code Particle#tick}, which most particle classes override without calling up.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "tickParticle", at = @At("HEAD"))
    private void zps$tractorBeamPull(Particle particle, CallbackInfo ci) {
        TractorBeamParticles.pull(particle);
    }
}
