package g_mungus.zps.contraption;

import org.joml.Matrix4f;

import com.mojang.math.Axis;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
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
 * context. It is consistent with {@link ServoMotorBlockEntity}'s renderer:
 * {@code world = anchor + CENTER + R(+angle)*(local - CENTER)} where {@code R}
 * is rotation about the motor's facing axis, matching
 * {@code ContraptionCollider.worldToLocalPos} (which uses {@code R(-angle)}).
 */
public final class ContraptionTransform {

	private final Vec3 anchorVec;
	private final float angle;
	private final Direction.Axis axis;

	public ContraptionTransform(Vec3 anchorVec, float angle, Direction.Axis axis) {
		this.anchorVec = anchorVec;
		this.angle = angle;
		this.axis = axis;
	}

	/** Transform for the interpolated client render angle at {@code partialTick}. */
	public static ContraptionTransform ofInterpolated(ServoMotorBlockEntity be, float partialTick) {
		return new ContraptionTransform(anchorOf(be), be.getInterpolatedAngle(partialTick), be.getRotationAxis());
	}

	/** Transform for the raw (server / current) angle. */
	public static ContraptionTransform ofCurrent(ServoMotorBlockEntity be) {
		return new ContraptionTransform(anchorOf(be), be.getAngle(), be.getRotationAxis());
	}

	private static Vec3 anchorOf(ServoMotorBlockEntity be) {
		return Vec3.atLowerCornerOf(be.getBlockPos().relative(be.getFacing()));
	}

	public float angle() {
		return angle;
	}

	public Direction.Axis axis() {
		return axis;
	}

	public Vec3 worldToLocal(Vec3 world) {
		return ContraptionMath.rotate(world.subtract(anchorVec).subtract(ContraptionMath.CENTER_OF_ORIGIN), -angle, axis)
			.add(ContraptionMath.CENTER_OF_ORIGIN);
	}

	public Vec3 localToWorld(Vec3 local) {
		return ContraptionMath.rotate(local.subtract(ContraptionMath.CENTER_OF_ORIGIN), angle, axis)
			.add(ContraptionMath.CENTER_OF_ORIGIN).add(anchorVec);
	}

	/** Rotate a world-space direction vector (no translation) into local space. */
	public Vec3 worldDirToLocal(Vec3 worldDir) {
		return ContraptionMath.rotate(worldDir, -angle, axis);
	}

	/** Nearest local-space {@link Direction} for a world-space direction vector. */
	public Direction worldDirToLocal(Direction worldDir) {
		return Direction.getNearest(worldDirToLocal(Vec3.atLowerCornerOf(worldDir.getNormal())));
	}

	/** World-space center of a local block position at the current rotation. */
	public Vec3 localBlockCenterToWorld(BlockPos local) {
		return localToWorld(Vec3.atCenterOf(local));
	}

	/**
	 * Build the pose that places local-space block geometry into the world,
	 * relative to {@code cameraPos} (so it can be drawn under a level-stage
	 * PoseStack). Mirrors the renderer's pivot rotation.
	 */
	public Matrix4f renderPose(Vec3 cameraPos) {
		Matrix4f pose = new Matrix4f();
		pose.translate((float) (anchorVec.x - cameraPos.x), (float) (anchorVec.y - cameraPos.y),
			(float) (anchorVec.z - cameraPos.z));
		pivotRotate(pose, axis, angle);
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
