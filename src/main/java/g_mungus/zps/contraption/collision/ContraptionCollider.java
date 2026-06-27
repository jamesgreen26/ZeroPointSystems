package g_mungus.zps.contraption.collision;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;

import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionRotationState;
import g_mungus.zps.contraption.collision.CollisionList.Populate;
import g_mungus.zps.contraption.collision.ContinuousOBBCollider.ContinuousSeparationManifold;
import g_mungus.zps.contraption.util.ContraptionMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pushes/carries entities that touch a rotating contraption. Driven from the
 * host BlockEntity's tick on BOTH sides: the server resolves non-player
 * entities, the client resolves its local player (player movement is
 * client-authoritative, so resolving it server-side would only fight the client
 * and feel like drag). Clean-room port of the core of Create's
 * ContraptionCollider (single rotation axis, no translation); no com.simibubi.* references.
 */
public final class ContraptionCollider {

	private ContraptionCollider() {}

	/**
	 * @param anchorVec     world position of the contraption's local origin
	 *                      (lower corner of the anchor block).
	 * @param worldBounds   generous world-space AABB enclosing the rotating structure.
	 * @param contactMotion maps a world point on the structure to the platform's
	 *                      velocity there this tick (for carrying riders).
	 * @param shouldCollide which nearby entities to resolve this tick (used to split
	 *                      player vs non-player handling by side).
	 */
	public static void collideEntities(Level level, Vec3 anchorVec, ContraptionRotationState rotation,
		Contraption contraption, AABB worldBounds, Function<Vec3, Vec3> contactMotion, Predicate<Entity> shouldCollide) {
		if (contraption == null || contraption.isEmpty())
			return;

		Vec3 contraptionMotion = Vec3.ZERO; // the bearing pivot never translates

		List<Entity> nearby = level.getEntitiesOfClass(Entity.class, worldBounds.inflate(2).expandTowards(0, 32, 0));
		for (Entity entity : nearby) {
			if (!entity.isAlive() || entity.isPassenger() || entity.noPhysics)
				continue;
			if (!shouldCollide.test(entity))
				continue;

			Matrix3d rotationMatrix = rotation.asMatrix();

			Vec3 entityPosition = entity.position();
			AABB entityBounds = entity.getBoundingBox();
			Vec3 motion = entity.getDeltaMovement();
			float yawOffset = rotation.getYawOffset();
			Vec3 position = getWorldToLocalTranslation(entity, anchorVec, rotationMatrix, yawOffset);

			motion = motion.subtract(contraptionMotion);
			motion = rotationMatrix.transform(motion);

			AABB localBB = entityBounds.move(position).inflate(1.0E-7D);
			OrientedBB obb = new OrientedBB(localBB);
			obb.setRotation(rotationMatrix);

			CollisionList collidableBBs = new CollisionList();
			getPotentiallyCollidedShapes(level, contraption, localBB.expandTowards(motion), new Populate(collidableBBs));
			if (collidableBBs.size == 0)
				continue;

			MutableObject<Vec3> collisionResponse = new MutableObject<>(Vec3.ZERO);
			MutableObject<Vec3> normal = new MutableObject<>(Vec3.ZERO);
			MutableObject<Vec3> location = new MutableObject<>(Vec3.ZERO);
			MutableBoolean surfaceCollision = new MutableBoolean(false);
			MutableFloat temporalResponse = new MutableFloat(1);
			Vec3 obbCenter = obb.getCenter();

			boolean doHorizontalPass = !rotation.hasVerticalRotation();
			for (boolean horizontalPass : new boolean[] { true, false }) {
				boolean verticalPass = !horizontalPass || !doHorizontalPass;

				for (int bbIdx = 0; bbIdx < collidableBBs.size; ++bbIdx) {
					Vec3 currentResponse = collisionResponse.getValue();
					Vec3 currentCenter = obbCenter.add(currentResponse);

					if (Math.abs(currentCenter.x - collidableBBs.centerX[bbIdx]) - entityBounds.getXsize() - 1 > collidableBBs.extentsX[bbIdx])
						continue;
					if (Math.abs((currentCenter.y + motion.y) - collidableBBs.centerY[bbIdx]) - entityBounds.getYsize() - 1 > collidableBBs.extentsY[bbIdx])
						continue;
					if (Math.abs(currentCenter.z - collidableBBs.centerZ[bbIdx]) - entityBounds.getZsize() - 1 > collidableBBs.extentsZ[bbIdx])
						continue;

					obb.setCenter(currentCenter);
					ContinuousSeparationManifold intersect = obb.intersect(collidableBBs, bbIdx, motion);
					if (intersect == null)
						continue;
					if (verticalPass && surfaceCollision.isFalse())
						surfaceCollision.setValue(intersect.isSurfaceCollision());

					double timeOfImpact = intersect.getTimeOfImpact();
					boolean isTemporal = timeOfImpact > 0 && timeOfImpact < 1;
					Vec3 collidingNormal = intersect.getCollisionNormal();
					Vec3 collisionPosition = intersect.getCollisionPosition();

					if (!isTemporal) {
						Vec3 separation = intersect.asSeparationVec(entity.maxUpStep());
						if (separation != null && !separation.equals(Vec3.ZERO)) {
							collisionResponse.setValue(currentResponse.add(separation));
							timeOfImpact = 0;
						}
					}

					boolean nearest = timeOfImpact >= 0 && temporalResponse.getValue() > timeOfImpact;
					if (collidingNormal != null && nearest)
						normal.setValue(collidingNormal);
					if (collisionPosition != null && nearest)
						location.setValue(collisionPosition);

					if (isTemporal && temporalResponse.getValue() > timeOfImpact)
						temporalResponse.setValue((float) timeOfImpact);
				}

				if (verticalPass)
					break;

				boolean noVerticalMotionResponse = temporalResponse.getValue() == 1;
				boolean noVerticalCollision = collisionResponse.getValue().y == 0;
				if (noVerticalCollision && noVerticalMotionResponse)
					break;

				collisionResponse.setValue(collisionResponse.getValue().multiply(129 / 128f, 0, 129 / 128f));
			}

			Vec3 entityMotion = entity.getDeltaMovement();
			Vec3 entityMotionNoTemporal = entityMotion;
			Vec3 collisionNormal = normal.getValue();
			Vec3 collisionLocation = location.getValue();
			Vec3 totalResponse = collisionResponse.getValue();
			boolean hardCollision = !totalResponse.equals(Vec3.ZERO);
			boolean temporalCollision = temporalResponse.getValue() != 1;
			Vec3 motionResponse = !temporalCollision ? motion
				: motion.normalize().scale(motion.length() * temporalResponse.getValue());

			rotationMatrix.transpose();
			motionResponse = rotationMatrix.transform(motionResponse).add(contraptionMotion);
			totalResponse = rotationMatrix.transform(totalResponse);
			totalResponse = ContraptionMath.rotate(totalResponse, yawOffset, Axis.Y);
			collisionNormal = rotationMatrix.transform(collisionNormal);
			collisionNormal = ContraptionMath.rotate(collisionNormal, yawOffset, Axis.Y).normalize();
			collisionLocation = rotationMatrix.transform(collisionLocation);
			collisionLocation = ContraptionMath.rotate(collisionLocation, yawOffset, Axis.Y);
			rotationMatrix.transpose();

			double bounce = 0;
			double slide = 0;

			if (!collisionLocation.equals(Vec3.ZERO)) {
				collisionLocation = collisionLocation
					.add(entity.position().add(entity.getBoundingBox().getCenter()).scale(.5f));
				if (temporalCollision)
					collisionLocation = collisionLocation.add(0, motionResponse.y, 0);

				BlockPos localPos = BlockPos.containing(
					worldToLocalPos(collisionLocation, anchorVec, rotationMatrix, yawOffset));
				StructureBlockInfo info = contraption.getBlocks().get(localPos);
				if (info != null) {
					BlockState blockState = info.state();
					bounce = getBounceMultiplier(blockState);
					slide = Math.max(0, blockState.getFriction(level, localPos, entity) - .6f);
				}
			}

			boolean hasNormal = !collisionNormal.equals(Vec3.ZERO);
			boolean anyCollision = hardCollision || temporalCollision;

			if (bounce > 0 && hasNormal && anyCollision && bounceEntity(entity, collisionNormal, contactMotion, bounce))
				continue;

			if (temporalCollision) {
				double idealVerticalMotion = motionResponse.y;
				if (idealVerticalMotion != entityMotion.y) {
					entity.setDeltaMovement(entityMotion.multiply(1, 0, 1).add(0, idealVerticalMotion, 0));
					entityMotion = entity.getDeltaMovement();
				}
			}

			if (hardCollision) {
				double motionX = entityMotion.x();
				double motionY = entityMotion.y();
				double motionZ = entityMotion.z();
				double intersectX = totalResponse.x();
				double intersectY = totalResponse.y();
				double intersectZ = totalResponse.z();

				double horizontalEpsilon = 1 / 128f;
				if (motionX != 0 && Math.abs(intersectX) > horizontalEpsilon && motionX > 0 == intersectX < 0)
					entityMotion = entityMotion.multiply(0, 1, 1);
				if (motionY != 0 && intersectY != 0 && motionY > 0 == intersectY < 0)
					entityMotion = entityMotion.multiply(1, 0, 1).add(0, contraptionMotion.y, 0);
				if (motionZ != 0 && Math.abs(intersectZ) > horizontalEpsilon && motionZ > 0 == intersectZ < 0)
					entityMotion = entityMotion.multiply(1, 1, 0);
			}

			if (bounce == 0 && slide > 0 && hasNormal && anyCollision && rotation.hasVerticalRotation()) {
				double slideFactor = collisionNormal.multiply(1, 0, 1).length() * 1.25f;
				Vec3 motionIn = entityMotionNoTemporal.multiply(0, .9, 0).add(0, -.01f, 0);
				Vec3 slideNormal = collisionNormal.cross(motionIn.cross(collisionNormal)).normalize();
				Vec3 newMotion = entityMotion.multiply(.85, 0, .85)
					.add(slideNormal.scale((.2f + slide) * motionIn.length() * slideFactor)
						.add(0, -.1f - collisionNormal.y * .125f, 0));
				entity.setDeltaMovement(newMotion);
				entityMotion = entity.getDeltaMovement();
			}

			if (!hardCollision && surfaceCollision.isFalse())
				continue;

			Vec3 allowedMovement = collide(totalResponse, entity);
			entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y + allowedMovement.y,
				entityPosition.z + allowedMovement.z);
			entityPosition = entity.position();
			entity.hurtMarked = true;

			if (surfaceCollision.isTrue()) {
				entity.fallDistance = 0;
				boolean canWalk = bounce != 0 || slide == 0;
				if (canWalk || !rotation.hasVerticalRotation())
					if (canWalk)
						entity.setOnGround(true);
				Vec3 contactPointMotion = contactMotion.apply(entityPosition);
				allowedMovement = collide(contactPointMotion, entity);
				entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y,
					entityPosition.z + allowedMovement.z);
			}

			entity.setDeltaMovement(entityMotion);
		}
	}

	private static double getBounceMultiplier(BlockState state) {
		return state.is(Blocks.SLIME_BLOCK) ? 0.8 : 0;
	}

	static boolean bounceEntity(Entity entity, Vec3 normal, Function<Vec3, Vec3> contactMotion, double factor) {
		if (factor == 0 || entity.isSuppressingBounce())
			return false;
		Vec3 contactPointMotion = contactMotion.apply(entity.position());
		Vec3 motion = entity.getDeltaMovement().subtract(contactPointMotion);
		Vec3 deltav = normal.scale(factor * 2 * motion.dot(normal));
		if (deltav.dot(deltav) < 0.1f)
			return false;
		entity.setDeltaMovement(entity.getDeltaMovement().subtract(deltav));
		return true;
	}

	public static Vec3 getWorldToLocalTranslation(Entity entity, Vec3 anchorVec, Matrix3d rotationMatrix,
		float yawOffset) {
		Vec3 entityPosition = entity.position();
		Vec3 centerY = new Vec3(0, entity.getBoundingBox().getYsize() / 2, 0);
		Vec3 position = entityPosition;
		position = position.add(centerY);
		position = worldToLocalPos(position, anchorVec, rotationMatrix, yawOffset);
		position = position.subtract(centerY);
		position = position.subtract(entityPosition);
		return position;
	}

	public static Vec3 worldToLocalPos(Vec3 worldPos, Vec3 anchorVec, Matrix3d rotationMatrix, float yawOffset) {
		Vec3 localPos = worldPos;
		localPos = localPos.subtract(anchorVec);
		localPos = localPos.subtract(ContraptionMath.CENTER_OF_ORIGIN);
		localPos = ContraptionMath.rotate(localPos, -yawOffset, Axis.Y);
		localPos = rotationMatrix.transform(localPos);
		localPos = localPos.add(ContraptionMath.CENTER_OF_ORIGIN);
		return localPos;
	}

	/** From Entity#collide — clip the requested movement against world block collisions. */
	static Vec3 collide(Vec3 movement, Entity e) {
		AABB aabb = e.getBoundingBox();
		List<VoxelShape> list = e.level().getEntityCollisions(e, aabb.expandTowards(movement));
		Vec3 vec3 = movement.lengthSqr() == 0.0D ? movement
			: Entity.collideBoundingBox(e, movement, aabb, e.level(), list);
		boolean flagX = movement.x != vec3.x;
		boolean flagY = movement.y != vec3.y;
		boolean flagZ = movement.z != vec3.z;
		boolean flagDown = flagY && movement.y < 0.0D;
		if (e.maxUpStep() > 0.0F && flagDown && (flagX || flagZ)) {
			Vec3 stepUp = Entity.collideBoundingBox(e, new Vec3(movement.x, e.maxUpStep(), movement.z), aabb, e.level(), list);
			Vec3 stepUpVertical = Entity.collideBoundingBox(e, new Vec3(0.0D, e.maxUpStep(), 0.0D),
				aabb.expandTowards(movement.x, 0.0D, movement.z), e.level(), list);
			if (stepUpVertical.y < e.maxUpStep()) {
				Vec3 stepUpHorizontal = Entity
					.collideBoundingBox(e, new Vec3(movement.x, 0.0D, movement.z), aabb.move(stepUpVertical), e.level(), list)
					.add(stepUpVertical);
				if (stepUpHorizontal.horizontalDistanceSqr() > stepUp.horizontalDistanceSqr())
					stepUp = stepUpHorizontal;
			}
			if (stepUp.horizontalDistanceSqr() > vec3.horizontalDistanceSqr())
				return stepUp.add(Entity.collideBoundingBox(e, new Vec3(0.0D, -stepUp.y + movement.y, 0.0D),
					aabb.move(stepUp), e.level(), list));
		}
		return vec3;
	}

	private static void getPotentiallyCollidedShapes(Level level, Contraption contraption, AABB localBB, Populate out) {
		double height = localBB.getYsize();
		double width = localBB.getXsize();
		double horizontalFactor = (height > width && width != 0) ? height / width : 1;
		double verticalFactor = (width > height && height != 0) ? width / height : 1;
		AABB blockScanBB = localBB.inflate(0.5f);
		blockScanBB = blockScanBB.inflate(horizontalFactor, verticalFactor, horizontalFactor);

		BlockPos min = BlockPos.containing(blockScanBB.minX, blockScanBB.minY, blockScanBB.minZ);
		BlockPos max = BlockPos.containing(blockScanBB.maxX, blockScanBB.maxY, blockScanBB.maxZ);

		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			StructureBlockInfo info = contraption.getBlocks().get(p);
			if (info == null)
				continue;
			VoxelShape collisionShape = info.state().getCollisionShape(level, p)
				.move(info.pos().getX(), info.pos().getY(), info.pos().getZ());
			if (!collisionShape.isEmpty())
				collisionShape.forAllBoxes(out);
		}
	}
}
