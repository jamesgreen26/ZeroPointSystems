package g_mungus.zps.gas;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.KelvinParticlePicker;

/**
 * Points Kelvin at one of our particle types, resolved on first use.
 *
 * <p>Kelvin's own {@code DefaultGasParticlePicker} takes the {@link ParticleOptions} up front,
 * which would force gas registration to wait until after the registries are populated. Gases need
 * to be registered during construction, so this defers the lookup to the first particle instead.
 */
public class LazyGasParticlePicker extends KelvinParticlePicker {

    private final DeferredHolder<ParticleType<?>, ? extends ParticleOptions> particleType;

    public LazyGasParticlePicker(DeferredHolder<ParticleType<?>, ? extends ParticleOptions> particleType) {
        this.particleType = particleType;
    }

    @Override
    public @NotNull ParticleOptions chooseParticleOptions(@NotNull Level level, @NotNull DuctNodePos pos) {
        return particleType.get();
    }
}
