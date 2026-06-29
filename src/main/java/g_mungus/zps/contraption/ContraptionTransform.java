package g_mungus.zps.contraption;

import org.joml.Matrix4f;

import com.mojang.math.Axis;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.collision.Matrix3d;
import g_mungus.zps.contraption.util.ContraptionMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Maps points and directions between world space and a contraption's
 * anchor-local space for the current rotation, and builds the render pose for
 * drawing local-space geometry (selection box, breaking overlay) in the world.
 *
 * <p>This is the single source of truth for the contraption transform shared by
 * the collider, the raytracer, the interaction renderer and the placement
 * context. A leaf transform is {@code world = R(+angle)*(local - CENTER) + CENTER + anchor}
 * (rotation about the motor's facing axis); it is stored as a general rigid map
 * {@code world = rotation*local + translation} so transforms can be
 * <em>composed</em>: a Servo Motor mounted on another contraption produces
 * {@code inner.andThen(parent)}, whose rotation is the (generally non-axis-aligned)
 * product of the two rotations. The leaf axis/angle are retained for callers that
 * need this motor's own rotation (block-state placement, falling-block spin).
 */
public final class ContraptionTransform {

	/** Local -> world rotation (composed for a nested transform). */
	private final Matrix3d rotation;
	/** World = rotation*local + translation. */
	private final Vec3 translation;
	/** Cached world -> local rotation ({@code rotation} transposed). */
	private final Matrix3d invRotation;
	/** This motor's own (un-composed) rotation, for placement/falling-block use. */
	private final Direction.Axis leafAxis;
	private final float leafAngle;

	private ContraptionTransform(Matrix3d rotation, Vec3 translation, Direction.Axis leafAxis, float leafAngle) {
		this.rotation = rotation;
		this.translation = translation;
		this.invRotation = rotation.copy().transpose();
		this.leafAxis = leafAxis;
		this.leafAngle = leafAngle;
	}

	/** A single (un-composed) contraption transform about {@code anchor}. */
	public static ContraptionTransform leaf(Vec3 anchor, float angle, Direction.Axis axis) {
		Matrix3d r = rotationMatrix(angle, axis);
		Vec3 t = ContraptionMath.CENTER_OF_ORIGIN.subtract(r.transform(ContraptionMath.CENTER_OF_ORIGIN)).add(anchor);
		return new ContraptionTransform(r, t, axis, angle);
	}

	/**
	 * Compose this (inner) transform with its {@code parent}: {@code this} maps inner-local into the
	 * parent's local space, {@code parent} maps that into world space. Retains <em>this</em>
	 * transform's leaf axis/angle (its own rotation relative to the immediate parent).
	 */
	public ContraptionTransform andThen(ContraptionTransform parent) {
		Matrix3d r = parent.rotation.copy().multiply(this.rotation);
		Vec3 t = parent.rotation.transform(this.translation).add(parent.translation);
		return new ContraptionTransform(r, t, this.leafAxis, this.leafAngle);
	}

	/** Transform for the interpolated client render angle at {@code partialTick}, composed through any host. */
	public static ContraptionTransform ofInterpolated(ServoMotorBlockEntity be, float partialTick) {
		ContraptionTransform self = leaf(anchorOf(be), be.getInterpolatedAngle(partialTick), be.getRotationAxis());
		ServoMotorBlockEntity host = be.hostMotor();
		return host == null ? self : self.andThen(ofInterpolated(host, partialTick));
	}

	/** Transform for the raw (server / current) angle, composed through any host contraption. */
	public static ContraptionTransform ofCurrent(ServoMotorBlockEntity be) {
		ContraptionTransform self = leaf(anchorOf(be), be.getAngle(), be.getRotationAxis());
		ServoMotorBlockEntity host = be.hostMotor();
		return host == null ? self : self.andThen(ofCurrent(host));
	}

	/**
	 * Transform for the pose every motor in the chain will hold after advancing one more tick by its
	 * <em>intended</em> per-tick spin ({@code running ? DEGREES_PER_TICK : 0}). Differencing
	 * {@code ofCurrent} and {@code ofIntendedNext} at a platform point yields its carried velocity,
	 * including a parent's angular velocity sweeping a nested pivot. Using the intended spin (not
	 * {@code angle - prevAngle}) makes it correct both for riders resolved <em>after</em> the angle
	 * advances and for a block detaching <em>before</em> it. Server-side.
	 */
	public static ContraptionTransform ofIntendedNext(ServoMotorBlockEntity be) {
		float next = be.getAngle() + be.getIntendedSpin();
		ContraptionTransform self = leaf(anchorOf(be), next, be.getRotationAxis());
		ServoMotorBlockEntity host = be.hostMotor();
		return host == null ? self : self.andThen(ofIntendedNext(host));
	}

	private static Vec3 anchorOf(ServoMotorBlockEntity be) {
		// For a nested motor this is its cell in the PARENT's local space; composition through the
		// host transform lifts it to world space.
		return Vec3.atLowerCornerOf(be.getBlockPos().relative(be.getFacing()));
	}

	private static Matrix3d rotationMatrix(float angleDeg, Direction.Axis axis) {
		float rad = (float) (angleDeg / 180d * Math.PI);
		return switch (axis) {
			case X -> new Matrix3d().asXRotation(rad);
			case Y -> new Matrix3d().asYRotation(rad);
			case Z -> new Matrix3d().asZRotation(rad);
		};
	}

	/** This motor's own rotation angle (NOT the composed angle); for placement / falling-block use. */
	public float angle() {
		return leafAngle;
	}

	/** This motor's own rotation axis (NOT meaningful for the composed rotation). */
	public Direction.Axis axis() {
		return leafAxis;
	}

	public Vec3 worldToLocal(Vec3 world) {
		return invRotation.transform(world.subtract(translation));
	}

	public Vec3 localToWorld(Vec3 local) {
		return rotation.transform(local).add(translation);
	}

	/** Rotate a world-space direction vector (no translation) into local space. */
	public Vec3 worldDirToLocal(Vec3 worldDir) {
		return invRotation.transform(worldDir);
	}

	/** Rotate a local-space direction vector (no translation) into world space. */
	public Vec3 localDirToWorld(Vec3 localDir) {
		return rotation.transform(localDir);
	}

	/** Nearest local-space {@link Direction} for a world-space direction vector. */
	public Direction worldDirToLocal(Direction worldDir) {
		return Direction.getNearest(worldDirToLocal(Vec3.atLowerCornerOf(worldDir.getNormal())));
	}

	/** World-space center of a local block position at the current rotation. */
	public Vec3 localBlockCenterToWorld(BlockPos local) {
		return localToWorld(Vec3.atCenterOf(local));
	}

	/** World -> local rotation (a fresh copy), e.g. for orienting collision boxes. */
	public Matrix3d worldToLocalRotation() {
		return invRotation.copy();
	}

	/** Local -> world rotation (a fresh copy). */
	public Matrix3d localToWorldRotation() {
		return rotation.copy();
	}

	/** Local -> world rotation as a quaternion, e.g. to tag a detached falling block (composed/nested). */
	public org.joml.Quaternionf localToWorldRotationQuat() {
		return rotation.toQuaternion();
	}

	/**
	 * World -> local rotation with the rotation about <em>world</em> Y (yaw) removed, used to orient a
	 * colliding entity's box: a turntable's rider turns with the platform (its box stays aligned to the
	 * contraption's axes), while a tilt keeps the rider world-aligned. Generalises the old
	 * {@code ContraptionRotationState.asMatrixNoYaw} to a composed rotation: it reproduces that method
	 * exactly for a single X/Y/Z axis and degrades gracefully for a tilted/nested rotation.
	 */
	public Matrix3d worldToLocalRotationNoWorldYaw() {
		Vec3 localXInWorld = rotation.transform(new Vec3(1, 0, 0));
		float yaw = (float) Math.atan2(-localXInWorld.z, localXInWorld.x);
		Matrix3d yawLocalToWorld = new Matrix3d().asYRotation(yaw);
		return invRotation.copy().multiply(yawLocalToWorld);
	}

	/**
	 * Whether the platform tilts off the horizontal (world up is not preserved by the composed
	 * rotation). For a plain Y-axis turntable this is {@code false}, matching the old
	 * {@code ContraptionRotationState.hasVerticalRotation}.
	 */
	public boolean hasVerticalRotation() {
		Vec3 up = rotation.transform(new Vec3(0, 1, 0));
		return Math.abs(up.x) > 1e-6 || Math.abs(up.z) > 1e-6 || up.y < 1 - 1e-6;
	}

	/**
	 * Build the pose that places local-space block geometry into the world,
	 * relative to {@code cameraPos} (so it can be drawn under a level-stage
	 * PoseStack). Works for composed (nested) transforms.
	 */
	public Matrix4f renderPose(Vec3 cameraPos) {
		Matrix4f pose = new Matrix4f();
		pose.translate((float) (translation.x - cameraPos.x), (float) (translation.y - cameraPos.y),
			(float) (translation.z - cameraPos.z));
		pose.mul(rotation.getAsMatrix4f());
		return pose;
	}

	/** Shared pivot rotation about a block's center used by the renderer and interaction. */
	public static void pivotRotate(Matrix4f pose, Direction.Axis axis, float angle) {
		pose.translate(0.5f, 0.5f, 0.5f);
		switch (axis) {
			case X -> pose.rotate(Axis.XP.rotationDegrees(angle));
			case Y -> pose.rotate(Axis.YP.rotationDegrees(angle));
			case Z -> pose.rotate(Axis.ZP.rotationDegrees(angle));
		}
		pose.translate(-0.5f, -0.5f, -0.5f);
	}
}
