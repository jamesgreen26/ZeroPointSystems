package g_mungus.zps.contraption;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.createmod.catnip.levelWrappers.WrappedServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

/**
 * Server-side companion to {@link ContraptionSimLevel}: a sim level that overlays a
 * contraption's captured blocks at their LOCAL positions (air everywhere else), but
 * backed by catnip's {@link WrappedServerLevel} so it is a <b>genuine {@link ServerLevel}</b>.
 *
 * <p>This is what lets the contraption actually <i>run</i>: because the engine sees a real
 * {@code ServerLevel}, the inherited {@code Level} machinery — {@code updateNeighborsAt}/
 * {@code neighborChanged}, the real {@code CollectingNeighborUpdater}, {@code getSignal}, and
 * {@code BlockState#tick} dispatch — all work natively, reading block state through our
 * {@link #getBlockState} override and scheduling through our {@link #scheduleTick} override.
 * So buttons, levers, doors, pressure plates and non-block-entity redstone behave as in a
 * normal world, with no re-implementation.
 *
 * <p>Reads come from the contraption and writes/ticks are routed into it (never the wrapped
 * real level): {@link #setBlock} mutates the contraption's block map (notifying the owner to
 * re-sync) and, when {@code UPDATE_NEIGHBORS} is set, propagates a redstone neighbour update;
 * {@link #getBlockTicks}/{@link #scheduleTick} use the contraption's own persistent tick queue.
 * Block entities are intentionally unsupported ({@link #getBlockEntity} returns {@code null}).
 *
 * <p>Constructing a {@code WrappedServerLevel} builds a full {@code ServerLevel}, so the owner
 * caches one instance per contraption rather than allocating per tick. Server-only — the client
 * uses the lightweight {@link ContraptionSimLevel}.
 */
public class ContraptionSimServerLevel extends WrappedServerLevel {

	private final Contraption contraption;
	/** Notified after a write so the host BlockEntity can re-sync ({@code setChanged}+sync). */
	@Nullable
	private final Runnable onChanged;
	/** Own (empty) fluid queue so any stray fluid tick never leaks onto the real level. */
	private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(cp -> true, () -> InactiveProfiler.INSTANCE);

	public ContraptionSimServerLevel(ServerLevel level, Contraption contraption, @Nullable Runnable onChanged) {
		super(level);
		this.contraption = contraption;
		this.onChanged = onChanged;
	}

	public Contraption getContraption() {
		return contraption;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		StructureBlockInfo info = contraption.getBlocks().get(pos);
		return info == null ? Blocks.AIR.defaultBlockState() : info.state();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
		return predicate.test(getBlockState(pos));
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags) {
		return setBlock(pos, state, flags, 512);
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
		BlockState old = getBlockState(pos);
		if (state == old)
			return false;
		if (state.isAir())
			contraption.removeBlock(pos);
		else
			contraption.putBlock(pos, state, null, null);

		// Block lifecycle, mirroring LevelChunk#setBlockState on the server: old#onRemove then
		// new#onPlace, with the new state already written above. This is essential for redstone:
		// a repeater/comparator toggles POWERED with flag 2 (no neighbour update) and notifies its
		// output purely through onPlace/onRemove -> updateNeighborsInFront. Skipping it left the
		// downstream wire's power un-recomputed.
		old.onRemove(this, pos, state, false);
		state.onPlace(this, pos, old, false);

		// Standard block-update propagation against the contraption (mirrors Level#markAndNotifyBlock,
		// minus the real-world client/chunk path — the host re-syncs the whole contraption).
		if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
			int childFlags = flags & ~(Block.UPDATE_NEIGHBORS | Block.UPDATE_SUPPRESS_DROPS);
			old.updateIndirectNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
			state.updateNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
			state.updateIndirectNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
		}
		// Redstone: notify the six neighbours that this cell changed. Safe to call here because
		// we are a real ServerLevel — the inherited CollectingNeighborUpdater queues and bounds
		// the cascade. This is what lights a lamp next to a placed redstone block, etc.
		if ((flags & Block.UPDATE_NEIGHBORS) != 0)
			updateNeighborsAt(pos, state.getBlock());
		if (onChanged != null)
			onChanged.run();
		return true;
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
		// A shape update can destroy an unsupported block (e.g. a torch). Just remove it from the
		// contraption — skip the real-world drop/effects path, which would spawn entities in the
		// wrapped level at the local position.
		if (getBlockState(pos).isAir())
			return false;
		return setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, recursionLeft);
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
		// No-op: the host BlockEntity re-syncs the whole contraption to clients after an operation.
	}

	@Override
	public LevelTicks<Block> getBlockTicks() {
		return contraption.getBlockTicks();
	}

	@Override
	public LevelTicks<Fluid> getFluidTicks() {
		return fluidTicks;
	}

	@Override
	public void scheduleTick(BlockPos pos, Block block, int delay) {
		scheduleTick(pos, block, delay, TickPriority.NORMAL);
	}

	@Override
	public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
		contraption.ensureTickContainer(pos);
		contraption.getBlockTicks().schedule(
			new ScheduledTick<>(block, pos, getLevelData().getGameTime() + delay, priority, nextSubTickCount()));
	}
}
