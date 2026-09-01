package g_mungus.zps.gas;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.KelvinParticlePicker;
import org.valkyrienskies.kelvin.impl.registry.GasParticlePickerRegistry;

/**
 * Draws a jet of gas particles out of a face, ported from Clockwork's {@code KelvinParticleHelper}
 * (Apache-2.0).
 *
 * <p>The details are what make it read as a jet rather than a puff of smoke:
 *
 * <ul>
 *   <li><b>Stochastic rounding</b> of the particle count, so a trickle still emits the occasional
 *       particle instead of flooring to zero.
 *   <li><b>Uniform disk sampling</b> across the mouth ({@code r = R·√u}); sampling {@code r}
 *       uniformly would bunch particles in the middle.
 *   <li><b>Trajectory jitter</b> — each particle is pushed forward by a random fraction of one
 *       tick's travel, so consecutive ticks blend into a stream instead of visible rings.
 *   <li><b>Velocity purely along the face normal</b>, so the jet stays collimated.
 * </ul>
 */
public final class GasJet {

    private static final int MAX_PARTICLES_PER_CALL = 32;
    public static final double DEFAULT_RADIUS = 0.3;

    private GasJet() {
    }

    /**
     * @param center         the emitting block's centre
     * @param outward        the face the jet leaves by
     * @param speed          particle speed, in blocks per tick
     * @param particleCount  how many to emit; fractional values emit probabilistically
     * @param outwardOffset  how far in front of the centre the jet starts
     */
    public static void spawn(Level level, DuctNodePos node, GasType gas, Vec3 center,
                             Direction outward, double speed, double particleCount,
                             double radius, double outwardOffset) {
        int count = stochasticCount(particleCount, level.random);
        if (count <= 0) {
            return;
        }

        KelvinParticlePicker picker = GasParticlePickerRegistry.INSTANCE.getParticlePicker(gas);
        if (picker == null) {
            return;
        }
        ParticleOptions options = picker.chooseParticleOptions(level, node);

        Direction.Axis axis = outward.getAxis();
        Vec3 first = axis == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 second = axis == Direction.Axis.Z ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);

        double nx = outward.getStepX();
        double ny = outward.getStepY();
        double nz = outward.getStepZ();

        for (int i = 0; i < count; i++) {
            // Uniform over the disk's area, not its radius.
            double r = radius * Math.sqrt(level.random.nextDouble());
            double theta = level.random.nextDouble() * 2.0 * Math.PI;
            double u = r * Math.cos(theta);
            double v = r * Math.sin(theta);

            // Spread each spawn along one tick of travel so the stream looks continuous.
            double forward = outwardOffset + level.random.nextDouble() * speed;

            double x = center.x + nx * forward + first.x * u + second.x * v;
            double y = center.y + ny * forward + first.y * u + second.y * v;
            double z = center.z + nz * forward + first.z * u + second.z * v;

            level.addParticle(options, x, y, z, nx * speed, ny * speed, nz * speed);
        }
    }

    /**
     * Rounds a fractional count without losing the fraction: 0.25 particles emits one particle a
     * quarter of the time. Capped so a large emitter cannot dump hundreds in a tick.
     */
    private static int stochasticCount(double count, RandomSource random) {
        if (count <= 0) {
            return 0;
        }
        int whole = (int) Math.floor(count);
        double fraction = count - whole;
        if (random.nextDouble() < fraction) {
            whole++;
        }
        return Math.min(whole, MAX_PARTICLES_PER_CALL);
    }
}
