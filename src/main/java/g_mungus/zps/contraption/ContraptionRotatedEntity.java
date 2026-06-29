package g_mungus.zps.contraption;

import org.joml.Quaternionf;

/**
 * Implemented (via mixin) by entities that can carry a contraption's orientation, so a
 * falling block detached from a rotating contraption keeps that rotation as it falls.
 * The rotation is a full {@link Quaternionf} (synced and persisted) so it can represent a
 * nested contraption's <em>composed</em> orientation (a product of rotations about
 * different axes), not just a single angle about one axis.
 */
public interface ContraptionRotatedEntity {

	/** Set the inherited orientation (local -&gt; world rotation of the source contraption). */
	void zps$setContraptionRotation(Quaternionf rotation);

	/** The inherited orientation, identity if none was set. */
	Quaternionf zps$getContraptionRotation();
}
