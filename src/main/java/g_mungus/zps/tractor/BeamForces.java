package g_mungus.zps.tractor;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.entity.TractorCargo;
import g_mungus.zps.mixin.AbstractArrowAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * How a Tractor Beam moves what is inside it. The same code runs on both sides: the server owns everything except
 * players, the client predicts items and falling blocks (their velocity is only sent every twentieth tick) and
 * moves its own player, whose movement the server takes on trust.
 */
public final class BeamForces {

    /** Entity types the beam ignores. */
    public static final TagKey<EntityType<?>> IMMUNE_ENTITIES =
            TagKey.create(Registries.ENTITY_TYPE, ZPSMod.resource("tractor_beam_immune"));

    /** Acceleration toward the mouth, in blocks per tick squared. */
    public static final double PULL_ACCELERATION = 0.08;
    /**
     * Fastest the beam moves anything, in blocks per tick. Under one block a tick, so that nothing can skip past
     * the collection zone in front of the mouth.
     */
    public static final double MAX_PULL_SPEED = 0.5;
    /** Living things stop this far out, rather than being pressed into the panel. */
    private static final double HOLD_DISTANCE = 1.0;
    /** Fraction of the sideways offset closed per tick. */
    private static final double CENTRING = 0.2;
    /** What the game leaves of a living thing's speed each tick in the air: sideways, and up and down. */
    private static final double AIR_DRAG = 0.91;
    private static final double FALL_DRAG = 0.98;

    private BeamForces() {
    }

    /** Whether the beam may touch {@code entity} at all, wherever it is. */
    public static boolean affects(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()) {
            return false;
        }
        if (entity instanceof HangingEntity || entity instanceof Display || entity instanceof Marker
                || entity instanceof Interaction || entity instanceof LightningBolt) {
            return false;
        }
        // The server flags an item as noPhysics while it is inside a block; it still wants pulling out. And a
        // falling block in a beam is noPhysics because the beam made it so; see TractorCargo. An arrow that is
        // noPhysics is a trident flying home to its owner, which is left to it.
        if (entity.noPhysics && !(entity instanceof ItemEntity || entity instanceof FallingBlockEntity)) {
            return false;
        }
        if (entity.getType().is(IMMUNE_ENTITIES)) {
            return false;
        }
        if (entity instanceof Player player) {
            return !resists(player);
        }
        return true;
    }

    /** Nothing pulls a player out of creative flight. On foot there is no way to dig in. */
    public static boolean resists(Player player) {
        return player.getAbilities().flying;
    }

    /**
     * Items, falling blocks and arrows are carried; everything else is pushed. Every arrow, whoever fired it: the
     * beam is not a player, and the rule about who may pick an arrow up is not its concern.
     */
    public static boolean isCargo(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof FallingBlockEntity || entity instanceof AbstractArrow;
    }

    /**
     * How far beside the beam a falling block may be and still be taken hold of. See {@link #grip}.
     */
    public static final double LOOSE_BLOCK_SLACK = 1.0;

    /**
     * Where {@code entity} is in the beam, or null when the beam does not have hold of it: outside the footprint,
     * beyond the range, or shielded by whatever ended its column.
     * <p>
     * Falling blocks are held to a looser standard, because the ones that matter most start out in the wrong
     * place. A block the beam pulls loose from its own grid sits squarely in a column. One pulled from a grid
     * lying at an angle to the beam, a ship from the world or the world from a ship, only had to poke into the
     * column to be found there: its centre is often in the next column along, whose reach may be shorter, or
     * outside the beam altogether. Held to the usual standard, such a block is pulled loose, never taken hold of,
     * and drops back where it was. So a falling block counts when it is within {@link #LOOSE_BLOCK_SLACK} of the
     * footprint, in the nearest column, and no further out than the beam's longest column reaches. It is then
     * drawn onto that column's centre line like any other, and nothing it passes on the way can snag it.
     */
    @Nullable
    public static Grip grip(Level level, BeamGeometry beam, BeamScan scan, Entity entity) {
        Vec3 world = entity.getBoundingBox().getCenter();
        Vec3 local = beam.toLocal(level, world);
        boolean looseBlock = entity instanceof FallingBlockEntity;

        int column = looseBlock ? beam.nearestColumn(local, LOOSE_BLOCK_SLACK) : beam.columnAt(local);
        if (column < 0) {
            return null;
        }
        double distance = beam.axialDistance(local);
        int reach = looseBlock ? scan.maxReach() : scan.reach(column);
        // One block of slack past the reach: that is the space of the block that ended the column, which is
        // where a block the beam has just pulled loose starts out.
        if (distance < 0 || distance > reach + 1.0) {
            return null;
        }
        return new Grip(world, local, column, distance);
    }

    public record Grip(Vec3 world, Vec3 local, int column, double distance) {
        public boolean atMouth() {
            return distance <= BeamGeometry.COLLECTION_DEPTH;
        }
    }

    /**
     * Moves {@code entity} for one tick. The caller has already checked {@link #affects} and found a grip.
     * <p>
     * Everything is worked out relative to {@code carrier}, the grid the panel rides, if it rides one. A beam on a
     * moving ship pulls toward a mouth that is itself moving, so "toward the mouth at this speed" has to mean in
     * the ship's frame, or what it carries falls behind and out of the beam.
     */
    public static void apply(Level level, BeamGeometry beam, Entity entity, Grip grip, @Nullable GridSpace carrier) {
        double maxSpeed = MAX_PULL_SPEED;
        double acceleration = PULL_ACCELERATION;
        Vec3 pull = beam.pullDirection();

        Vec3 carrierVelocity = carrier == null ? Vec3.ZERO : carrier.velocityAt(grip.world());
        Vec3 velocity = beam.toLocalVector(level, grip.world(), entity.getDeltaMovement().subtract(carrierVelocity));
        double toward = velocity.dot(pull);

        Vec3 result;
        if (isCargo(entity)) {
            // Carried down the middle of its own column, so a block pulled loose never clips its neighbours.
            double speed = grip.atMouth() ? 0.0 : Mth.clamp(toward + acceleration, 0.0, maxSpeed);
            Vec3 sideways = beam.onColumnAxis(grip.column(), grip.local()).subtract(grip.local()).scale(CENTRING);
            result = pull.scale(speed).add(clampLength(sideways, maxSpeed * 0.5));
        } else {
            Vec3 sideways = beam.onBeamAxis(grip.local()).subtract(grip.local()).scale(CENTRING * acceleration);
            if (grip.distance() > HOLD_DISTANCE + entity.getBbWidth() * 0.5) {
                double push = Math.min(acceleration, Math.max(0.0, maxSpeed - toward));
                result = velocity.add(pull.scale(push)).add(sideways);
            } else {
                // Held: bleed off whatever speed it arrived with instead of grinding it into the panel.
                result = velocity.scale(0.8).add(sideways);
            }
        }

        Vec3 worldVelocity = beam.toWorldVector(level, grip.local(), result).add(carrierVelocity);
        if (!isCargo(entity)) {
            // Cargo has its velocity set outright each tick. Anything else keeps its own, and the game's drag
            // will slow that against the world. Hand back what that drag takes from the carrier's share of it,
            // which turns drag against the world into drag against the ship.
            worldVelocity = worldVelocity.add(carrierVelocity.multiply(1.0 - AIR_DRAG, 1.0 - FALL_DRAG, 1.0 - AIR_DRAG));
        }
        // Cancels the entity's own gravity step next tick, so the beam carries its weight and lets go cleanly:
        // the moment the beam stops, it simply falls. A player is weightless by its own no-gravity flag instead
        // (see PlayerWeightlessness), and getGravity() answers zero for as long as that is set.
        entity.setDeltaMovement(worldVelocity.add(0.0, gravityOf(entity), 0.0));
        entity.hasImpulse = true;
        entity.resetFallDistance();

        if (entity instanceof FallingBlockEntity falling) {
            // A falling block gives up and drops as an item after 600 ticks in the air.
            falling.time = 1;
        }
        if (entity instanceof AbstractArrow arrow && ((AbstractArrowAccessor) arrow).zps$isInGround()) {
            // Stuck in a block, an arrow ignores its velocity; pulled loose, it flies like any other.
            ((AbstractArrowAccessor) arrow).zps$setInGround(false);
        }
        if (entity instanceof TractorCargo cargo) {
            cargo.zps$carriedByBeam();
        }
    }

    private static Vec3 clampLength(Vec3 vector, double max) {
        double length = vector.length();
        return length > max ? vector.scale(max / length) : vector;
    }

    /**
     * The gravity step an entity takes each tick, as this version's entities hard-code it: living
     * things and most others fall by 0.08, items and falling blocks by 0.04, arrows by 0.05, and
     * anything with gravity switched off by nothing at all.
     */
    public static double gravityOf(Entity entity) {
        if (entity.isNoGravity()) {
            return 0.0;
        }
        if (entity instanceof FallingBlockEntity || entity instanceof ItemEntity) {
            return 0.04;
        }
        if (entity instanceof AbstractArrow) {
            return 0.05;
        }
        return 0.08;
    }
}
