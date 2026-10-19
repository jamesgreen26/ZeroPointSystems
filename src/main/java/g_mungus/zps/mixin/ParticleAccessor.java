package g_mungus.zps.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a particle's velocity, which vanilla only lets the outside world set, for the Tractor Beam's
 * pull; and unsticks one vanilla has stopped for good, for gas spreading along a surface.
 */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("xd")
    double zps$getXd();

    @Accessor("yd")
    double zps$getYd();

    @Accessor("zd")
    double zps$getZd();

    @Accessor("stoppedByCollision")
    void zps$setStoppedByCollision(boolean stopped);
}
