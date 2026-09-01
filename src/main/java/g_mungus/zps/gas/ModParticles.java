package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle;

/**
 * Particle types for ZPS's gases.
 *
 * <p>Kelvin can register a particle for a gas itself, but only into its own deferred registry,
 * which it submits during its own construction — an entry added by another mod afterwards never
 * resolves, and the client crashes dereferencing it. So ZPS registers its gas particles in its own
 * namespace and hands Kelvin a picker pointing at them.
 */
public final class ModParticles {

    private ModParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, ZPSMod.MOD_ID);

    /** Reuses Kelvin's gas particle, so ZPS gases look like every other gas. */
    public static final DeferredHolder<ParticleType<?>, DefaultGasParticle.DefaultGasParticleType> FLUX =
            PARTICLE_TYPES.register("flux", DefaultGasParticle.DefaultGasParticleType::new);
}
