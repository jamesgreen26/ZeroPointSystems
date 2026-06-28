package g_mungus.zps.contraption;

import net.minecraft.core.Direction;

/**
 * Implemented (via mixin) by entities that can carry a contraption's orientation, so a
 * falling block detached from a rotating contraption keeps that rotation as it falls.
 * The rotation is a single angle (degrees) about one of the contraption's axes, synced
 * to clients and persisted, matching how {@link ContraptionTransform} rotates the
 * structure.
 */
public interface ContraptionRotatedEntity {

	/** Set the inherited rotation (degrees about {@code axis}). */
	void zps$setContraptionRotation(float angleDeg, Direction.Axis axis);

	float zps$getContraptionAngle();

	Direction.Axis zps$getContraptionAxis();
}
