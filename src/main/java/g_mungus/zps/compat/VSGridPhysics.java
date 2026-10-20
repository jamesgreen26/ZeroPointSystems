package g_mungus.zps.compat;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * {@link GridPhysics} over one Valkyrien Skies physics tick, on VS's physics thread. Only reached through
 * {@code mixin.vs.PhysicsListenerExtension}.
 * <p>
 * VS takes steady forces in the ship's model axes at shipyard positions, as its own test thruster does, and
 * integrates them over the tick itself. Its centre of mass is the transform's position in the ship.
 * <p>
 * VS weighs a plain block at {@link #KG_PER_BLOCK}, Sable at 1, so masses are divided by that and forces and
 * torques multiplied by it to keep both mods in Sable's units.
 * <p>
 * VS treats a scaled ship's model forces and torques as though for its blocks at full size: it scales forces by
 * the scale cubed, as the mass goes, and torques by its fifth power, as the inertia goes. Both are undone here,
 * to keep to the world's units, which its mass is in already.
 */
public final class VSGridPhysics implements GridPhysics {
    /** VS's default mass for a block, in kilograms. */
    static final double KG_PER_BLOCK = 1000;

    private final PhysShip ship;

    public VSGridPhysics(PhysShip ship) {
        this.ship = ship;
    }

    @Override
    public void applyForce(Vec3 point, Vec3 force) {
        double scale = scale();
        ship.applyModelForce(VectorConversionsMCKt.toJOML(force.scale(KG_PER_BLOCK / (scale * scale * scale))),
                VectorConversionsMCKt.toJOML(point));
    }

    @Override
    public void applyTorque(Vec3 torque) {
        double scale = scale();
        ship.applyModelTorque(VectorConversionsMCKt.toJOML(torque.scale(KG_PER_BLOCK / Math.pow(scale, 5))));
    }

    @Override
    public Vec3 linearVelocity() {
        return VectorConversionsMCKt.toMinecraft(ship.getVelocity());
    }

    @Override
    public Vec3 angularVelocity() {
        return VectorConversionsMCKt.toMinecraft(ship.getAngularVelocity());
    }

    @Override
    public Vec3 position() {
        return VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInWorld());
    }

    @Override
    public double mass() {
        return ship.getMass() / KG_PER_BLOCK;
    }

    @Override
    public double scale() {
        return ship.getTransform().getShipToWorldScaling().x();
    }

    @Override
    public Vec3 centreOfMass() {
        return VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInShip());
    }

    @Override
    public Vec3 toWorldDirection(Vec3 direction) {
        return VectorConversionsMCKt.toMinecraft(ship.getTransform()
                .transformDirectionNoScalingFromShipToWorld(VectorConversionsMCKt.toJOML(direction), new Vector3d()));
    }

    @Override
    public Vec3 toGridDirection(Vec3 direction) {
        return VectorConversionsMCKt.toMinecraft(ship.getTransform()
                .transformDirectionNoScalingFromWorldToShip(VectorConversionsMCKt.toJOML(direction), new Vector3d()));
    }
}
