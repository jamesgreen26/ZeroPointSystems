package g_mungus.zps.compat;

import net.minecraft.world.phys.Vec3;

/**
 * The grid a {@link GridPhysicsActor} is on, for the length of one physics step: what can be done to it and how
 * it is moving. Not to be kept past the call it was handed to.
 * <p>
 * Positions and the vectors applied are in the grid's own block space, the coordinates its blocks really have,
 * so a block entity can use its own {@code getBlockPos()} and facing as they are. What is read back about the
 * motion is in the world.
 * <p>
 * Masses, forces and torques are in units of a plain block weighing 1; Valkyrien Skies' kilograms are scaled
 * to match, at its default 1000 per block. Speeds are in blocks per second, not per tick as in {@link GridSpace}.
 * <p>
 * Masses, forces, torques and speeds are the world's, whatever the grid's {@link #scale}: a grid at half scale
 * weighs an eighth of what its blocks would at full size, and a force pushes it as hard as it would any other
 * body of that mass.
 */
public interface GridPhysics {

    /** Pushes steadily at {@code point}, the force in the grid's own axes. Held for this step only. */
    void applyForce(Vec3 point, Vec3 force);

    /** Turns steadily, about the grid's own axes. Held for this step only. */
    void applyTorque(Vec3 torque);

    /** How fast the centre of mass moves through the world, in blocks per second. */
    Vec3 linearVelocity();

    /** How fast the grid turns, about world axes, in radians per second. */
    Vec3 angularVelocity();

    /** How fast the grid at {@code point} moves through the world, turning included, in blocks per second. */
    default Vec3 velocityAt(Vec3 point) {
        Vec3 arm = toWorldDirection(point.subtract(centreOfMass()));
        return linearVelocity().add(angularVelocity().cross(arm));
    }

    /** Where the grid is in the world: the position of its pose. */
    Vec3 position();

    /** The grid's mass, a plain block at full scale weighing 1. */
    double mass();

    /**
     * How long one of the grid's blocks is in the world: 1 but for a scaled grid, as Genesis shrinks every ship in
     * its space dimensions to 1/16.
     */
    default double scale() {
        return 1;
    }

    /** The centre of mass, in the grid's block space. */
    Vec3 centreOfMass();

    /** {@code direction}, in the grid's own axes, turned into the world's. */
    Vec3 toWorldDirection(Vec3 direction);

    /** {@code direction}, in the world's axes, turned into the grid's own. */
    Vec3 toGridDirection(Vec3 direction);
}
