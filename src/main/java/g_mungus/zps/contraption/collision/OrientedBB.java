package g_mungus.zps.contraption.collision;

import g_mungus.zps.contraption.collision.ContinuousOBBCollider.ContinuousSeparationManifold;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An oriented bounding box used for sweeping an entity AABB against a rotated
 * contraption. Clean-room port of Create's collision OrientedBB (no com.simibubi.* dependency).
 */
public class OrientedBB {

	Vec3 center;
	Vec3 extents;
	Matrix3d rotation;

	public OrientedBB(AABB bb) {
		this(bb.getCenter(), extentsFromBB(bb), new Matrix3d().asIdentity());
	}

	public OrientedBB() {
		this(Vec3.ZERO, Vec3.ZERO, new Matrix3d().asIdentity());
	}

	public OrientedBB(Vec3 center, Vec3 extents, Matrix3d rotation) {
		this.setCenter(center);
		this.extents = extents;
		this.setRotation(rotation);
	}

	public OrientedBB copy() {
		return new OrientedBB(center, extents, rotation);
	}

	public ContinuousSeparationManifold intersect(CollisionList collisionList, int bbIdx, Vec3 motion) {
		Vec3 centerA = new Vec3(collisionList.centerX[bbIdx], collisionList.centerY[bbIdx], collisionList.centerZ[bbIdx]);
		Vec3 extentsA = new Vec3(collisionList.extentsX[bbIdx], collisionList.extentsY[bbIdx], collisionList.extentsZ[bbIdx]);
		return ContinuousOBBCollider.separateBBs(centerA, center, extentsA, extents, rotation, motion);
	}

	private static Vec3 extentsFromBB(AABB bb) {
		return new Vec3(bb.getXsize() / 2, bb.getYsize() / 2, bb.getZsize() / 2);
	}

	public Matrix3d getRotation() {
		return rotation;
	}

	public void setRotation(Matrix3d rotation) {
		this.rotation = rotation;
	}

	public Vec3 getCenter() {
		return center;
	}

	public void setCenter(Vec3 center) {
		this.center = center;
	}

	public void move(Vec3 offset) {
		setCenter(getCenter().add(offset));
	}

	public AABB getAsAABB() {
		return new AABB(0, 0, 0, 0, 0, 0).move(center)
			.inflate(extents.x, extents.y, extents.z);
	}

}
