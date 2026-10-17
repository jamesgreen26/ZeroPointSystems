package g_mungus.zps.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle;

/**
 * A puff of gas that floats or sinks by how dense it is against what it is let out into.
 *
 * <p>Looks and moves like Kelvin's {@code DefaultGasParticle}, except for buoyancy. Kelvin's judges a
 * gas only by its type against plain air, never lets it sink, and adds a constant lift on top. This
 * one takes a buoyancy from the block letting it out ({@link #setBuoyancy}) — worked out on the
 * server against the room or atmosphere actually there, temperature included — so hot or light gas
 * rises, cold or heavy gas sinks, and gas let out into vacuum falls.
 */
public class GasParticle extends TextureSheetParticle {

    /** Upward acceleration at a buoyancy of 1, in blocks per tick squared; downward at -1. */
    private static final double BUOYANCY_ACCELERATION = 0.02;
    /**
     * Buoyancy is gated by speed: at high velocity inertia dominates, so a jet does not visibly
     * U-turn the moment it leaves the mouth.
     */
    private static final double BUOYANCY_GATING_K = 200.0;
    private static final float START_SIZE_FACTOR = 0.4f;
    private static final float SIZE_GROWTH = 4f;
    private static final double LIFETIME_MULT = 1.75;
    private static final float FADE_OUT_START = 0.60f;
    private static final double VELOCITY_JITTER = 0.15;
    private static final double MIN_JITTER = 0.005;

    private final SpriteSet sprites;
    private final float baseSize;
    /** From 1, rising hard, to -1, falling freely. */
    private double buoyancy;

    protected GasParticle(ClientLevel level, double x, double y, double z,
                          double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, float gasDensity) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        gravity = 0f;
        friction = 0.94f;

        double speed = Math.sqrt(xSpeed * xSpeed + ySpeed * ySpeed + zSpeed * zSpeed);
        double jitter = Math.max(speed * VELOCITY_JITTER, MIN_JITTER);
        xd = xSpeed + (random.nextDouble() - 0.5) * 2.0 * jitter;
        yd = ySpeed + (random.nextDouble() - 0.5) * 2.0 * jitter;
        zd = zSpeed + (random.nextDouble() - 0.5) * 2.0 * jitter;

        baseSize = quadSize * START_SIZE_FACTOR;
        quadSize = baseSize;
        lifetime = (int) (lifetime * LIFETIME_MULT);
        // Until told otherwise, float as the gas would in plain air at the same temperature.
        double ratio = gasDensity / DefaultGasParticle.AIR_DENSITY;
        buoyancy = (1 - ratio) / Math.max(1, ratio);
        setSpriteFromAge(sprites);
    }

    /**
     * Add a particle for {@code options} and, if it is one of these, give it {@code buoyancy}. Goes
     * straight to the particle engine, since {@code Level#addParticle} hands nothing back, so it
     * keeps the level renderer's distance cut-off and minimal-particles setting itself.
     */
    public static void spawn(ParticleOptions options, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed, double buoyancy) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || camera.getPosition().distanceToSqr(x, y, z) > MAX_DISTANCE_SQ
                || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Particle particle = minecraft.particleEngine.createParticle(options, x, y, z, xSpeed, ySpeed, zSpeed);
        if (particle instanceof GasParticle gas) {
            gas.setBuoyancy(buoyancy);
        }
    }

    /** As far as the level renderer draws particles from the camera, squared. */
    private static final double MAX_DISTANCE_SQ = 32 * 32;

    /** See {@code RoomManager#buoyancy}. NaN leaves it as it is. */
    public void setBuoyancy(double buoyancy) {
        if (!Double.isNaN(buoyancy)) {
            this.buoyancy = buoyancy;
        }
    }

    @Override
    public void tick() {
        super.tick();
        double speedSq = xd * xd + yd * yd + zd * zd;
        yd += buoyancy * BUOYANCY_ACCELERATION / (1.0 + BUOYANCY_GATING_K * speedSq);

        float lifeFrac = (float) age / lifetime;
        quadSize = baseSize * (1f + lifeFrac * SIZE_GROWTH);
        alpha = lifeFrac < FADE_OUT_START ? 1f : Math.max(0f, (1f - lifeFrac) / (1f - FADE_OUT_START));
        setSpriteFromAge(sprites);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** For ZPS's gas particle types, which carry their gas's density as Kelvin's do. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            float density = type instanceof DefaultGasParticle.DefaultGasParticleType gas
                    ? gas.getDensity() : DefaultGasParticle.AIR_DENSITY;
            return new GasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, density);
        }
    }
}
