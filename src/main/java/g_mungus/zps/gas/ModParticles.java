package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZPSMod.MOD_ID);

    /**
     * Kelvin's gas particle type, drawn by ZPS's {@code GasParticle} so vents and leaks can say how
     * it floats in what it goes into; it looks like every other gas.
     *
     * <p>The particle type carries the gas density, which drives the particle's buoyancy until a
     * vent or leak says otherwise — flux is far lighter than air, so it rises hard. Not a method reference: the density parameter has a
     * Kotlin default, so the class exposes both a no-arg and a one-arg constructor and
     * {@code DefaultGasParticleType::new} is ambiguous.
     */
    public static final RegistryObject<DefaultGasParticle.DefaultGasParticleType> FLUX =
            PARTICLE_TYPES.register("flux",
                    () -> new DefaultGasParticle.DefaultGasParticleType((float) ModGases.FLUX_DENSITY));

    /** Aether's, for when ZPS defines it itself; Clockwork registers its own otherwise. */
    public static final RegistryObject<DefaultGasParticle.DefaultGasParticleType> AETHER =
            PARTICLE_TYPES.register("aether",
                    () -> new DefaultGasParticle.DefaultGasParticleType((float) ModGases.AETHER_DENSITY));

    /** Steam's, for when ZPS defines it itself; Clockwork registers its own otherwise. */
    public static final RegistryObject<DefaultGasParticle.DefaultGasParticleType> STEAM =
            PARTICLE_TYPES.register("steam",
                    () -> new DefaultGasParticle.DefaultGasParticleType((float) ModGases.STEAM_DENSITY));

    /** Stellane's, for when ZPS defines it itself; Clockwork registers its own otherwise. */
    public static final RegistryObject<DefaultGasParticle.DefaultGasParticleType> STELLANE =
            PARTICLE_TYPES.register("stellane",
                    () -> new DefaultGasParticle.DefaultGasParticleType((float) ModGases.STELLANE_DENSITY));
}
