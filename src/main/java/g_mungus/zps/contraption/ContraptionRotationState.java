package g_mungus.zps.contraption;

import g_mungus.zps.contraption.collision.Matrix3d;
import net.minecraft.core.Direction.Axis;

/**
 * Describes the current rotation of a contraption for the collider. Mirrors the
 * relevant parts of Create's ContraptionRotationState. {@link #asMatrix()}
 * returns the WORLD-to-LOCAL rotation (note the negated angles), i.e. the
 * inverse of the local-to-world rotation used by the renderer.
 */
public class ContraptionRotationState {

	public static final ContraptionRotationState NONE = new ContraptionRotationState(null, 0);

	public float xRotation = 0;
	public float yRotation = 0;
	public float zRotation = 0;

	public ContraptionRotationState(Axis axis, float angle) {
		if (axis == Axis.X)
			xRotation = angle;
		else if (axis == Axis.Y)
			yRotation = angle;
		else if (axis == Axis.Z)
			zRotation = angle;
	}

	public Matrix3d asMatrix() {
		Matrix3d matrix = new Matrix3d().asIdentity();
		if (xRotation != 0)
			matrix.multiply(new Matrix3d().asXRotation(rad(-xRotation)));
		if (yRotation != 0)
			matrix.multiply(new Matrix3d().asYRotation(rad(-yRotation)));
		if (zRotation != 0)
			matrix.multiply(new Matrix3d().asZRotation(rad(-zRotation)));
		return matrix;
	}

	/**
	 * World→local rotation EXCLUDING the Y (yaw) component, used as a colliding entity's
	 * box orientation so it shares the contraption's Y rotation (stays yaw-aligned with a
	 * turntable instead of keeping its world-aligned footprint). For X/Z-axis contraptions
	 * this equals {@link #asMatrix()} (no yaw to remove).
	 */
	public Matrix3d asMatrixNoYaw() {
		Matrix3d matrix = new Matrix3d().asIdentity();
		if (xRotation != 0)
			matrix.multiply(new Matrix3d().asXRotation(rad(-xRotation)));
		if (zRotation != 0)
			matrix.multiply(new Matrix3d().asZRotation(rad(-zRotation)));
		return matrix;
	}

	public boolean hasVerticalRotation() {
		return xRotation != 0 || zRotation != 0;
	}

	public float getYawOffset() {
		return 0;
	}

	private static float rad(float degrees) {
		return (float) (degrees / 180d * Math.PI);
	}
}
