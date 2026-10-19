package g_mungus.zps.client.particle;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.mixin.ParticleAccessor;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle;

/**
 * A puff of gas that floats or sinks by how dense it is against what it is let out into.
 *
 * <p>Looks and moves like Kelvin's {@code DefaultGasParticle}, except for buoyancy and surfaces. Kelvin's
 * judges a gas only by its type against plain air, never lets it sink, and adds a constant lift on top.
 * This one takes a buoyancy from the block letting it out ({@link #setBuoyancy}) — worked out on the
 * server against the room or atmosphere actually there, temperature included — so hot or light gas
 * rises, cold or heavy gas sinks, and gas let out into vacuum falls. And where Kelvin's stops dead
 * against a surface, this one spreads out along it ({@link #move}).
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
    /** Of the speed going into a surface, the share turned along it; the rest is lost to the hit. */
    private static final double SPREAD = 0.6;
    /** How far short of its move a particle has to come to count as having hit something, in blocks. */
    private static final double MIN_BLOCKED = 1e-6;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

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
     * Whether particles at {@code pos} are worth adding: the level renderer's own distance cut-off and
     * minimal-particles setting, which {@link #spawn} goes around. The distance is to where {@code pos}
     * is in the world: a vent on a sublevel spawns in the sublevel's plot, far from the camera, and
     * Sable moves the particle out to the sublevel itself once it is added — it patches the level
     * renderer's own check to match, not this one.
     */
    public static boolean worthSpawningAt(Level level, Vec3 pos) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        return camera.isInitialized()
                && camera.getPosition().distanceToSqr(Compat.toWorldPos(level, pos)) <= MAX_DISTANCE_SQ
                && minecraft.options.particles().get() != ParticleStatus.MINIMAL;
    }

    /**
     * Add a particle for {@code options} and, if it is one of these, give it {@code buoyancy}. Goes
     * straight to the particle engine, since {@code Level#addParticle} hands nothing back; check
     * {@link #worthSpawningAt} first.
     */
    public static void spawn(ParticleOptions options, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed, double buoyancy) {
        Minecraft minecraft = Minecraft.getInstance();
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

    /**
     * As vanilla's, except a surface turns the gas aside rather than stopping it: the speed going
     * into it goes along it instead, each particle its own random way, so a jet hitting a wall fans
     * out across it. The move itself is vanilla's, so Sable still collides it with sublevels; what
     * was hit is told from how far short of the move the particle came, which works for a sublevel's
     * turned blocks as well as the world's.
     */
    @Override
    public void move(double dx, double dy, double dz) {
        double startX = x;
        double startY = y;
        double startZ = z;
        double startXd = xd;
        double startYd = yd;
        double startZd = zd;
        super.move(dx, dy, dz);
        // Vanilla stops a particle for good the first time anything blocks it vertically.
        ((ParticleAccessor) this).zps$setStoppedByCollision(false);

        // What a surface took off the move points into it.
        double shortX = dx - (x - startX);
        double shortY = dy - (y - startY);
        double shortZ = dz - (z - startZ);
        double shortLength = Math.sqrt(shortX * shortX + shortY * shortY + shortZ * shortZ);
        if (shortLength < MIN_BLOCKED) {
            return;
        }
        Vec3 normal = new Vec3(shortX / shortLength, shortY / shortLength, shortZ / shortLength);
        double into = startXd * normal.x + startYd * normal.y + startZd * normal.z;
        if (into <= 0) {
            return;
        }
        // Vanilla stops only the sideways part; whatever is still headed into the surface stops too.
        double still = xd * normal.x + yd * normal.y + zd * normal.z;
        if (still > 0) {
            xd -= normal.x * still;
            yd -= normal.y * still;
            zd -= normal.z * still;
        }
        spread(normal, into);
    }

    /** Turn {@code speed} into a surface facing along {@code normal} into a random way along it. */
    private void spread(Vec3 normal, double speed) {
        Vec3 first = normal.cross(Math.abs(normal.y) < 0.9 ? UP : EAST).normalize();
        Vec3 second = normal.cross(first);
        double angle = random.nextDouble() * 2.0 * Math.PI;
        double along = speed * SPREAD;
        double cos = along * Math.cos(angle);
        double sin = along * Math.sin(angle);
        xd += first.x * cos + second.x * sin;
        yd += first.y * cos + second.y * sin;
        zd += first.z * cos + second.z * sin;
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
