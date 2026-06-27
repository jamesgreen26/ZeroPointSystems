package g_mungus.zps.contraption;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;

/**
 * The data model for a movable structure: a set of captured blocks stored in
 * anchor-local coordinates, the structure bounds, and the anchor (pivot) world
 * position. Host-agnostic — the Servo Motor BlockEntity owns an instance.
 *
 * <p>Clean-room reimplementation of the relevant parts of Create's Contraption;
 * no com.simibubi.* references.
 */
public class Contraption {

	/** Safety cap on how many blocks a single contraption may contain. */
	public static final int MAX_BLOCKS = 4096;

	private final Map<BlockPos, StructureBlockInfo> blocks = new HashMap<>();
	/** Client-render NBT (getUpdateTag) per local pos, for reconstructing block entities on the client. */
	private final Map<BlockPos, CompoundTag> updateTags = new HashMap<>();
	private BlockPos anchor = BlockPos.ZERO;
	@Nullable
	private AABB bounds;

	public Map<BlockPos, StructureBlockInfo> getBlocks() {
		return blocks;
	}

	public Map<BlockPos, CompoundTag> getUpdateTags() {
		return updateTags;
	}

	public BlockPos getAnchor() {
		return anchor;
	}

	public AABB getBounds() {
		return bounds == null ? new AABB(BlockPos.ZERO) : bounds;
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	/**
	 * Shallow copy of the structure (block infos and update tags are immutable /
	 * treated as such). Used for client-side prediction, where a fresh instance is
	 * needed so the renderer/collider pick up the change (they key on identity).
	 */
	public Contraption copy() {
		Contraption copy = new Contraption();
		copy.anchor = anchor;
		copy.blocks.putAll(blocks);
		copy.updateTags.putAll(updateTags);
		copy.bounds = bounds;
		return copy;
	}

	/**
	 * Flood-fill outward from the anchor, capturing all connected movable blocks
	 * (excluding the controller block) into local coordinates.
	 */
	public void assemble(Level level, BlockPos anchor, BlockPos controllerPos) throws AssemblyException {
		this.anchor = anchor;
		blocks.clear();
		bounds = null;

		Queue<BlockPos> frontier = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(anchor);

		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.poll();
			if (!visited.add(pos))
				continue;
			if (pos.equals(controllerPos))
				continue;
			if (!level.isLoaded(pos))
				throw AssemblyException.unloadedChunk(pos);

			BlockState state = level.getBlockState(pos);
			if (!BlockMovementChecks.isMovementNecessary(state))
				continue; // air / boundary: do not capture or expand through
			if (!BlockMovementChecks.isMovementAllowed(state, level, pos))
				continue; // unmovable block acts as a wall

			addBlock(level, pos, state);
			if (blocks.size() > MAX_BLOCKS)
				throw AssemblyException.tooLarge();

			for (Direction dir : Direction.values())
				frontier.add(pos.relative(dir));
		}

		if (blocks.isEmpty())
			throw AssemblyException.empty();
	}

	private void addBlock(Level level, BlockPos pos, BlockState state) {
		BlockPos local = pos.subtract(anchor);
		BlockEntity be = level.getBlockEntity(pos);
		CompoundTag nbt = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
		blocks.put(local, new StructureBlockInfo(local, state, nbt));
		if (be != null)
			updateTags.put(local, be.getUpdateTag(level.registryAccess()));
		expandBounds(local);
	}

	private void expandBounds(BlockPos local) {
		AABB box = new AABB(local);
		bounds = bounds == null ? box : bounds.minmax(box);
	}

	/** Recompute the structure bounds from scratch (after a block is removed). */
	private void recomputeBounds() {
		bounds = null;
		for (BlockPos local : blocks.keySet())
			expandBounds(local);
	}

	/**
	 * Insert or replace a block in the structure at a local position (used by
	 * in-flight placement). Returns false if the position lies outside the loaded
	 * structure entirely (never null state expected from callers).
	 */
	public void putBlock(BlockPos local, BlockState state, @Nullable CompoundTag beNbt, @Nullable CompoundTag updateTag) {
		blocks.put(local, new StructureBlockInfo(local, state, beNbt));
		if (updateTag != null)
			updateTags.put(local, updateTag);
		else
			updateTags.remove(local);
		expandBounds(local);
	}

	/**
	 * Remove a block from the structure (used by in-flight mining). Returns the
	 * removed info, or null if there was no block there.
	 */
	@Nullable
	public StructureBlockInfo removeBlock(BlockPos local) {
		StructureBlockInfo removed = blocks.remove(local);
		if (removed == null)
			return null;
		updateTags.remove(local);
		recomputeBounds();
		return removed;
	}

	/** Remove all captured blocks from the world (brittle first, then solid). */
	public void removeBlocksFromWorld(Level level) {
		for (boolean brittlePass : new boolean[] { true, false }) {
			for (StructureBlockInfo info : blocks.values()) {
				BlockPos worldPos = info.pos().offset(anchor);
				if (BlockMovementChecks.isBrittle(info.state(), level, worldPos) != brittlePass)
					continue;
				level.removeBlockEntity(worldPos);
				level.setBlock(worldPos, Blocks.AIR.defaultBlockState(),
					Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Write all captured blocks back into the world at the transformed positions. */
	public void addBlocksToWorld(Level level, StructureTransform transform) {
		for (boolean solidPass : new boolean[] { true, false }) {
			for (StructureBlockInfo info : blocks.values()) {
				BlockPos target = transform.apply(info.pos());
				BlockState state = transform.apply(info.state());
				boolean solid = !BlockMovementChecks.isBrittle(state, level, target);
				if (solid != solidPass)
					continue;

				level.destroyBlock(target, false);
				level.setBlock(target, state, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);

				CompoundTag data = info.nbt();
				if (data == null)
					continue;
				BlockEntity be = level.getBlockEntity(target);
				if (be == null)
					continue;
				CompoundTag tag = data.copy();
				tag.putInt("x", target.getX());
				tag.putInt("y", target.getY());
				tag.putInt("z", target.getZ());
				be.loadWithComponents(tag, level.registryAccess());
			}
		}
	}

	public CompoundTag writeNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Anchor", anchor.asLong());

		ListTag list = new ListTag();
		for (StructureBlockInfo info : blocks.values()) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("Pos", info.pos().asLong());
			entry.put("State", NbtUtils.writeBlockState(info.state()));
			if (info.nbt() != null)
				entry.put("Data", info.nbt());
			CompoundTag updateTag = updateTags.get(info.pos());
			if (updateTag != null)
				entry.put("UpdateTag", updateTag);
			list.add(entry);
		}
		tag.put("Blocks", list);
		return tag;
	}

	public void readNBT(HolderLookup.Provider registries, CompoundTag tag) {
		blocks.clear();
		updateTags.clear();
		bounds = null;
		anchor = BlockPos.of(tag.getLong("Anchor"));

		HolderGetter<Block> blockGetter = registries.lookupOrThrow(Registries.BLOCK);
		ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			BlockPos local = BlockPos.of(entry.getLong("Pos"));
			BlockState state = NbtUtils.readBlockState(blockGetter, entry.getCompound("State"));
			CompoundTag data = entry.contains("Data") ? entry.getCompound("Data") : null;
			blocks.put(local, new StructureBlockInfo(local, state, data));
			if (entry.contains("UpdateTag"))
				updateTags.put(local, entry.getCompound("UpdateTag"));
			expandBounds(local);
		}
	}
}
