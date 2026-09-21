package g_mungus.zps.client.tractor;

import g_mungus.zps.tractor.BeamForces;
import g_mungus.zps.mixin.ParticleAccessor;
import g_mungus.zps.tractor.BeamGeometry;
import g_mungus.zps.tractor.RunningBeams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Particles in a Tractor Beam. It makes none of its own; it drags any particle at all that drifts into it while
 * it runs, and swallows the ones that reach the mouth.
 */
public final class TractorBeamParticles {
    /** Particles this close to the mouth are swallowed. */
    private static final double SWALLOW_DISTANCE = 0.25;
    /** Particles are only dragged by beams within this many blocks of the camera. */
    private static final double CAMERA_RANGE = 64.0;

    private TractorBeamParticles() {
    }

    /** Called for every particle, every tick, so the way out for the common case comes first and costs nothing. */
    public static void pull(Particle particle) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (RunningBeams.noneIn(level)) {
            return;
        }
        Vec3 pos = particle.getPos();
        for (RunningBeams.Entry entry : RunningBeams.in(level)) {
            if (!entry.worldBounds().contains(pos) || !nearCamera(minecraft, entry)) {
                continue;
            }
            BeamGeometry beam = entry.geometry();
            Vec3 local = beam.toLocal(level, pos);
            int column = beam.columnAt(local);
            if (column < 0) {
                continue;
            }
            double distance = beam.axialDistance(local);
            if (distance < 0 || distance > entry.scan().reach(column) + 1.0) {
                continue;
            }
            if (distance < SWALLOW_DISTANCE) {
                particle.remove();
                return;
            }

            ParticleAccessor motion = (ParticleAccessor) particle;
            Vec3 velocity = new Vec3(motion.zps$getXd(), motion.zps$getYd(), motion.zps$getZd());
            Vec3 pull = beam.toWorldVector(level, local, beam.pullDirection());
            double toward = velocity.dot(pull);
            double push = Math.min(BeamForces.PULL_ACCELERATION,
                    Math.max(0.0, BeamForces.MAX_PULL_SPEED - toward));
            Vec3 result = velocity.add(pull.scale(push));
            particle.setParticleSpeed(result.x, result.y, result.z);
            return;
        }
    }

    private static boolean nearCamera(Minecraft minecraft, RunningBeams.Entry entry) {
        double range = CAMERA_RANGE;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        return entry.worldBounds().distanceToSqr(camera) <= range * range;
    }
}
