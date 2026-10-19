package g_mungus.zps.compat;

import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * {@link GridPhysics} over one Sable physics substep. Only reached through {@code mixin.sable.SubLevelActorExtension}.
 * <p>
 * Sable takes what a substep adds as impulses, in the sublevel's own axes and at plot positions, as its own
 * propellers do; a steady force is that times the substep's length. Its body velocities are in the world.
 * <p>
 * Rapier puts a body to sleep once it has moved slowly for a while, forces or not, and Sable only wakes one when
 * the queued push changes by more than a little. A sublevel sinking slowly under a steady thrust would fall
 * asleep and hang in the air, its velocity reading zero. Pushing keeps it awake.
 */
public final class SableGridPhysics implements GridPhysics {
    private final ServerSubLevel subLevel;
    private final RigidBodyHandle handle;
    private final double timeStep;
    private boolean woken;

    public SableGridPhysics(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        this.subLevel = subLevel;
        this.handle = handle;
        this.timeStep = timeStep;
    }

    @Override
    public void applyForce(Vec3 point, Vec3 force) {
        if (force.lengthSqr() > 0) {
            wakeUp();
        }
        propulsion().applyAndRecordPointForce(JOMLConversion.toJOML(point), JOMLConversion.toJOML(force.scale(timeStep)));
    }

    @Override
    public void applyTorque(Vec3 torque) {
        if (torque.lengthSqr() > 0) {
            wakeUp();
        }
        propulsion().getForceTotal().applyTorqueImpulse(JOMLConversion.toJOML(torque.scale(timeStep)));
    }

    private void wakeUp() {
        if (!woken) {
            woken = true;
            SubLevelContainer.getContainer(subLevel.getLevel()).physicsSystem().getPipeline().wakeUp(subLevel);
        }
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
    public Vec3 position() {
        return JOMLConversion.toMojang(subLevel.logicalPose().position());
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
