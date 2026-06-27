package g_mungus.zps.contraption.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal vendored vector/angle helpers used by the contraption system, so the
 * collision code and entity transforms stay free of any external library
 * (no com.simibubi.* and no catnip dependency).
 */
public final class ContraptionMath {

	/** The center of the block at the origin (0.5, 0.5, 0.5). */
	public static final Vec3 CENTER_OF_ORIGIN = new Vec3(0.5, 0.5, 0.5);

	private ContraptionMath() {}

	/** Center of a block position, i.e. its lower corner plus (0.5, 0.5, 0.5). */
	public static Vec3 getCenterOf(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	/** Rotate a vector around the center of the origin block (0.5, 0.5, 0.5). */
	public static Vec3 rotateCentered(Vec3 vec, double deg, Axis axis) {
		return rotate(vec.subtract(CENTER_OF_ORIGIN), deg, axis).add(CENTER_OF_ORIGIN);
	}

	/** Rotate a vector by the given degrees around the given axis. */
	public static Vec3 rotate(Vec3 vec, double deg, Axis axis) {
		if (deg == 0)
			return vec;

		float angle = (float) (deg / 180d * Math.PI);
		double sin = Mth.sin(angle);
		double cos = Mth.cos(angle);
		double x = vec.x;
		double y = vec.y;
		double z = vec.z;

		return switch (axis) {
			case X -> new Vec3(x, y * cos - z * sin, y * sin + z * cos);
			case Y -> new Vec3(x * cos + z * sin, y, z * cos - x * sin);
			case Z -> new Vec3(x * cos - y * sin, x * sin + y * cos, z);
		};
	}

	/** Interpolate between two angles (degrees) accounting for wrap-around. */
	public static float angleLerp(float pct, float current, float target) {
		return current + pct * getShortestAngleDiff(current, target);
	}

	/** Shortest signed difference between two angles in degrees, in range (-180, 180]. */
	public static float getShortestAngleDiff(float current, float target) {
		current = current % 360;
		target = target % 360;
		return (float) (((((target - current) % 360) + 540) % 360) - 180);
	}
}
