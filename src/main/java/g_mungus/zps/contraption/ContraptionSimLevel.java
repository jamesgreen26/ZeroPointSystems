package g_mungus.zps.contraption;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.createmod.catnip.levelWrappers.WrappedLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

/**
 * A {@link WrappedLevel} that overlays a contraption's captured blocks at their
 * LOCAL positions (air everywhere else). Used as the placement-simulation level so
 * that vanilla {@code getStateForPlacement}/{@code canSurvive} resolve states with
 * the contraption's own blocks as neighbours, and as the level that in-structure
 * block side-effects (e.g. {@code FallingBlock#onPlace}) run against.
 *
 * <p>Reads come from the contraption. Writes and scheduled block ticks are routed
 * into the contraption instead of the wrapped real level: {@link #setBlock} mutates
 * the contraption's block map (and notifies the owner to re-sync), and
 * {@link #getBlockTicks} returns the contraption's own tick queue so scheduled ticks
 * stay separate from the outer world. Works on both sides (client prediction and
 * authoritative server placement); the client passes a {@code null} owner since it
 * never writes or drives ticks.
 */
public class ContraptionSimLevel extends WrappedLevel {

	private final Contraption contraption;
	/** Notified after a write so the host BlockEntity can re-sync ({@code setChanged}+sync); null on the client. */
	@Nullable
	private final Runnable onChanged;
	/** Own (empty) fluid queue so any stray fluid tick never leaks onto the real level. */
	private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(cp -> true, () -> InactiveProfiler.INSTANCE);

	public ContraptionSimLevel(Level level, Contraption contraption) {
		this(level, contraption, null);
	}

	public ContraptionSimLevel(Level level, Contraption contraption, @Nullable Runnable onChanged) {
		super(level);
		this.contraption = contraption;
		this.onChanged = onChanged;
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

		// Standard block-update propagation against the contraption (mirrors Level#markAndNotifyBlock,
		// minus the real-world client/chunk path — the host re-syncs the whole contraption). This is
		// what lets e.g. a FallingBlock above a removed block reschedule its fall.
		if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
			int childFlags = flags & ~(Block.UPDATE_NEIGHBORS | Block.UPDATE_SUPPRESS_DROPS);
			old.updateIndirectNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
			state.updateNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
			state.updateIndirectNeighbourShapes(this, pos, childFlags, recursionLeft - 1);
		}
		if (onChanged != null)
			onChanged.run();
		return true;
	}

	@Override
	public void neighborShapeChanged(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos,
		int flags, int recursionLevel) {
		// Level routes this through a CollectingNeighborUpdater, but WrappedLevel constructs Level with
		// a 0 update budget so it would silently drop the update. Apply it directly instead.
		NeighborUpdater.executeShapeUpdate(this, direction, neighborState, pos, neighborPos, flags, recursionLevel);
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
	public LevelTickAccess<Block> getBlockTicks() {
		return contraption.getBlockTicks();
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
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
