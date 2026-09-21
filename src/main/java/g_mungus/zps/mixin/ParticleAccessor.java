package g_mungus.zps.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a particle's velocity, which vanilla only lets the outside world set. For the Tractor Beam's pull. */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("xd")
    double zps$getXd();

    @Accessor("yd")
    double zps$getYd();

    @Accessor("zd")
    double zps$getZd();
}
