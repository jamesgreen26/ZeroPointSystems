package g_mungus.zps.contraption;

import java.util.function.Consumer;

import g_mungus.zps.contraption.collision.ContraptionCollider;
import g_mungus.zps.contraption.collision.Matrix3d;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ContraptionPlacementUtil {

	private ContraptionPlacementUtil() {}

	public static boolean placeBlock(Level blockEntityLevel, Level sim, Contraption contraption, Player player,
		ItemStack stack, ContraptionPlaceContext.Placed placed) {
		BlockPos pos = placed.pos();
		BlockState state = placed.state();
		if (!sim.setBlock(pos, state, Block.UPDATE_ALL))
			return false;

		BlockState placedState = sim.getBlockState(pos);
		if (placedState.is(state.getBlock()))
			placedState.getBlock().setPlacedBy(sim, pos, placedState, player, stack);

		storeBlockEntityData(blockEntityLevel, contraption, pos);
		return true;
	}

	private static void storeBlockEntityData(Level level, Contraption contraption, BlockPos pos) {
		StructureBlockInfo info = contraption.getBlocks().get(pos);
		if (info == null)
			return;

		BlockState state = info.state();
		if (!(state.getBlock() instanceof EntityBlock entityBlock))
			return;

		BlockEntity be = entityBlock.newBlockEntity(pos, state);
		if (be == null)
			return;

		be.setLevel(level);
		CompoundTag beNbt = be.saveWithFullMetadata(level.registryAccess());
		CompoundTag updateTag = be.getUpdateTag(level.registryAccess());
		contraption.putBlock(pos, state, beNbt, updateTag);
	}

	public static boolean isUnobstructed(Level worldLevel, Level localLevel, Player player, BlockPos localPos,
		BlockState state, ContraptionTransform transform) {
		CollisionContext collisionContext = player == null ? CollisionContext.empty() : CollisionContext.of(player);
		VoxelShape localShape = state.getCollisionShape(localLevel, localPos, collisionContext);
		if (localShape.isEmpty())
			return true;

		AABB worldBounds = localShapeWorldBounds(localShape, localPos, transform);
		Matrix3d entityRotation = new ContraptionRotationState(transform.axis(), transform.angle()).asMatrixNoYaw();
		for (Entity entity : worldLevel.getEntities((Entity) null, worldBounds,
			entity -> !entity.isRemoved() && entity.blocksBuilding)) {
			AABB localEntityBox = worldBoxToLocalAabb(entity.getBoundingBox(), transform);
			if (ContraptionCollider.intersectsShape(localShape, localPos, localEntityBox, entityRotation))
				return false;
		}
		return true;
	}

	private static AABB worldBoxToLocalAabb(AABB worldBox, ContraptionTransform transform) {
		Vec3 localCenter = transform.worldToLocal(worldBox.getCenter());
		return new AABB(localCenter, localCenter)
			.inflate(worldBox.getXsize() / 2.0D, worldBox.getYsize() / 2.0D, worldBox.getZsize() / 2.0D);
	}

	private static AABB localShapeWorldBounds(VoxelShape localShape, BlockPos localPos, ContraptionTransform transform) {
		MutableAabb bounds = new MutableAabb();
		localShape.move(localPos.getX(), localPos.getY(), localPos.getZ())
			.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
				forEachCorner(new AABB(minX, minY, minZ, maxX, maxY, maxZ),
					corner -> bounds.include(transform.localToWorld(corner))));
		return bounds.toAabb();
	}

	private static void forEachCorner(AABB box, Consumer<Vec3> consumer) {
		double[] xs = { box.minX, box.maxX };
		double[] ys = { box.minY, box.maxY };
		double[] zs = { box.minZ, box.maxZ };
		for (double x : xs)
			for (double y : ys)
				for (double z : zs)
					consumer.accept(new Vec3(x, y, z));
	}

	private static final class MutableAabb {
		private double minX = Double.POSITIVE_INFINITY;
		private double minY = Double.POSITIVE_INFINITY;
		private double minZ = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY;
		private double maxY = Double.NEGATIVE_INFINITY;
		private double maxZ = Double.NEGATIVE_INFINITY;

		private void include(Vec3 point) {
			minX = Math.min(minX, point.x);
			minY = Math.min(minY, point.y);
			minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y);
			maxZ = Math.max(maxZ, point.z);
		}

		private AABB toAabb() {
			return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}
}
