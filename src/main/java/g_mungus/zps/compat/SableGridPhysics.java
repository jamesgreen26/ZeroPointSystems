package g_mungus.zps.compat;

import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * {@link GridPhysics} over one Sable physics substep. Only reached through {@code mixin.sable.SubLevelActorExtension}.
 * <p>
 * Sable takes what a substep adds as impulses, in the sublevel's own axes and at plot positions, as its own
 * propellers do; a steady force is that times the substep's length. Its body velocities are in the world.
 */
public final class SableGridPhysics implements GridPhysics {
    private final ServerSubLevel subLevel;
    private final RigidBodyHandle handle;
    private final double timeStep;

    public SableGridPhysics(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        this.subLevel = subLevel;
        this.handle = handle;
        this.timeStep = timeStep;
    }

    @Override
    public void applyForce(Vec3 point, Vec3 force) {
        propulsion().applyAndRecordPointForce(JOMLConversion.toJOML(point), JOMLConversion.toJOML(force.scale(timeStep)));
    }

    @Override
    public void applyTorque(Vec3 torque) {
        propulsion().getForceTotal().applyTorqueImpulse(JOMLConversion.toJOML(torque.scale(timeStep)));
    }

    private QueuedForceGroup propulsion() {
        return subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
    }

    @Override
    public Vec3 linearVelocity() {
        return JOMLConversion.toMojang(handle.getLinearVelocity(new Vector3d()));
    }

    @Override
    public Vec3 angularVelocity() {
        return JOMLConversion.toMojang(handle.getAngularVelocity(new Vector3d()));
    }

    @Override
    public double mass() {
        return subLevel.getMassTracker().getMass();
    }

    @Override
    public Vec3 centreOfMass() {
        return JOMLConversion.toMojang(subLevel.getMassTracker().getCenterOfMass());
    }

    @Override
    public Vec3 toWorldDirection(Vec3 direction) {
        return subLevel.logicalPose().transformNormal(direction);
    }

    @Override
    public Vec3 toGridDirection(Vec3 direction) {
        return subLevel.logicalPose().transformNormalInverse(direction);
    }
}
