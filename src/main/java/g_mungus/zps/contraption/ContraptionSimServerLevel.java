package g_mungus.zps.contraption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import g_mungus.zps.contraption.util.ContraptionMath;
import g_mungus.zps.mixin.EntityAccessor;
import net.createmod.catnip.levelWrappers.WrappedServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
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
 *
 * <p><b>Live block entities are supported here</b> (unlike the client {@link ContraptionSimLevel}):
 * {@link #getBlockEntity} lazily reconstructs a live {@link BlockEntity} from the captured
 * {@code beNbt}, bound to this level at the LOCAL position, and caches it. The owner ticks them
 * ({@link #getTickingBlockEntities}) so furnaces smelt / hoppers move items / comparators read
 * containers, and {@link #flushAllBlockEntityData} writes their live state back into the
 * contraption before save/sync. Pistons remain out of scope.
 *
 * <p>Constructing a {@code WrappedServerLevel} builds a full {@code ServerLevel}, so the owner
 * caches one instance per contraption rather than allocating per tick. Server-only — the client
 * uses the lightweight {@link ContraptionSimLevel}.
 */
public class ContraptionSimServerLevel extends WrappedServerLevel {

	private static final double RAD_TO_DEG = 180.0 / Math.PI;

	/** The real wrapped level, kept directly because {@link WrappedServerLevel} stubs some methods. */
	private final ServerLevel realLevel;
	private final Contraption contraption;
	/** Notified after a write so the host BlockEntity can mark itself dirty ({@code setChanged}). */
	@Nullable
	private final Runnable onChanged;
	/**
	 * Notified when a change needs to reach clients (a structural {@link #setBlock} or a block
	 * entity raising {@link #sendBlockUpdated}). The host flips its {@code simNeedsSync} flag so the
	 * whole contraption is re-synced once at the end of the tick. Kept distinct from {@link #onChanged}
	 * (persistence) because {@code blockEntityChanged} fires every hopper/furnace tick and must NOT
	 * trigger a network resync.
	 */
	@Nullable
	private final Runnable onNeedsSync;

	/** Live block entities, keyed by LOCAL position; lazily built from {@code beNbt} in {@link #getBlockEntity}. */
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
	/** Cached list of tickable block entities, rebuilt when {@link #tickersDirty} (block add/remove). */
	@Nullable
	private List<TickingBE> tickers;
	private boolean tickersDirty = true;

	/** A live block entity paired with its resolved server ticker. */
	public record TickingBE(BlockPos pos, BlockEntity be, BlockEntityTicker<BlockEntity> ticker) {
	}
	/**
	 * Supplies the contraption's current world pose, used to play block sounds (which the engine
	 * emits at LOCAL block coordinates) at their actual, rotated world position. Null when no pose
	 * is available (e.g. in tests), in which case sounds fall through untransformed.
	 */
	@Nullable
	private final Supplier<ContraptionTransform> transform;
	/** Own (empty) fluid queue so any stray fluid tick never leaks onto the real level. */
	private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(cp -> true, () -> InactiveProfiler.INSTANCE);

	public ContraptionSimServerLevel(ServerLevel level, Contraption contraption, @Nullable Runnable onChanged,
		@Nullable Runnable onNeedsSync, @Nullable Supplier<ContraptionTransform> transform) {
		super(level);
		this.realLevel = level;
		this.contraption = contraption;
		this.onChanged = onChanged;
		this.onNeedsSync = onNeedsSync;
		this.transform = transform;
	}

	public Contraption getContraption() {
		return contraption;
	}

	/** The contraption's current world pose, or {@code null} when none is available (e.g. tests). */
	@Nullable
	public ContraptionTransform getTransform() {
		return transform == null ? null : transform.get();
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
		BlockEntity existing = blockEntities.get(pos);
		if (existing != null)
			return existing;
		StructureBlockInfo info = contraption.getBlocks().get(pos);
		if (info == null)
			return null;
		BlockState state = info.state();
		if (!(state.getBlock() instanceof EntityBlock entityBlock))
			return null;
		BlockPos immutable = pos.immutable();
		BlockEntity be = entityBlock.newBlockEntity(immutable, state);
		if (be == null)
			return null;
		be.setLevel(this);
		if (info.nbt() != null)
			be.loadWithComponents(info.nbt(), registryAccess());
		blockEntities.put(immutable, be);
		return be;
	}

	/** Drop a live block entity from the cache (e.g. its host block was removed/replaced). */
	public void removeBlockEntity(BlockPos pos) {
		BlockEntity removed = blockEntities.remove(pos);
		if (removed != null)
			removed.setRemoved();
		tickersDirty = true;
	}

	/**
	 * The contraption's tickable block entities with their resolved server tickers. Rebuilt lazily
	 * whenever a block was added/removed/replaced (so a newly placed furnace starts ticking and a
	 * broken one stops). Driven once per server tick by the Servo Motor.
	 */
	public List<TickingBE> getTickingBlockEntities() {
		if (tickers == null || tickersDirty)
			rebuildTickers();
		return tickers;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void rebuildTickers() {
		List<TickingBE> rebuilt = new ArrayList<>();
		for (StructureBlockInfo info : contraption.getBlocks().values()) {
			BlockState state = info.state();
			if (!(state.getBlock() instanceof EntityBlock entityBlock))
				continue;
			BlockEntity be = getBlockEntity(info.pos());
			if (be == null)
				continue;
			BlockEntityTicker<BlockEntity> ticker =
				(BlockEntityTicker<BlockEntity>) entityBlock.getTicker(this, state, (BlockEntityType) be.getType());
			if (ticker == null)
				continue;
			rebuilt.add(new TickingBE(info.pos(), be, ticker));
		}
		tickers = rebuilt;
		tickersDirty = false;
	}

	/**
	 * Write every live block entity's current state back into the contraption (full {@code beNbt}
	 * for persistence/loot and {@code updateTag} for client rendering). Called by the host before a
	 * save or network sync so the serialized structure reflects live BE state (smelt progress,
	 * container contents, …).
	 */
	public void flushAllBlockEntityData() {
		for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
			BlockPos pos = entry.getKey();
			BlockEntity be = entry.getValue();
			StructureBlockInfo info = contraption.getBlocks().get(pos);
			if (info == null)
				continue;
			contraption.putBlock(pos, info.state(), be.saveWithFullMetadata(registryAccess()),
				be.getUpdateTag(registryAccess()));
		}
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
		return predicate.test(getBlockState(pos));
	}

	/**
	 * The wrapped level's chunk source is a dummy that reports no loaded chunks, but several redstone
	 * paths gate on {@code hasChunkAt} — notably {@code updateNeighbourForOutputSignal}, which a
	 * container raises so an adjacent comparator recomputes. Reads are served from the contraption map
	 * (see {@link #getBlockState}/{@link #getBlockEntity}), so it is safe to report every position as
	 * present; out-of-structure neighbours simply read as air.
	 */
	@Override
	public boolean hasChunkAt(BlockPos pos) {
		return true;
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

		// Block-entity lifecycle, mirroring LevelChunk#setBlockState: drop a live BE whose host block
		// no longer supports it (the new one, if any, is created lazily by getBlockEntity). A pure
		// blockstate change on the same BE-bearing block (e.g. a furnace toggling LIT) keeps it.
		BlockEntity liveBE = blockEntities.get(pos);
		if (liveBE != null && !liveBE.getType().isValid(state))
			removeBlockEntity(pos);
		if (old.hasBlockEntity() || state.hasBlockEntity())
			tickersDirty = true;

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
		// A structural change must reach clients (redstone, a furnace toggling LIT, falling blocks).
		if (onNeedsSync != null)
			onNeedsSync.run();
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

	/**
	 * An entity a block/block-entity tick tries to spawn (a dispenser's projectile, dropped items, a
	 * primed TNT, smelting XP, …) is created against THIS sim level at contraption-LOCAL coordinates —
	 * but the sim level isn't ticked or tracked by the server, so it would be orphaned. Redirect it to
	 * the real outer level at the matching rotated WORLD position, with its velocity rotated into world
	 * space, so it behaves like it spawned off the moving structure. Falls back to a plain spawn when
	 * no pose is available (e.g. tests).
	 */
	@Override
	public boolean addFreshEntity(Entity entity) {
		((EntityAccessor) entity).zps$setLevel(realLevel);
		ContraptionTransform t = getTransform();
		if (t != null) {
			Vec3 world = t.localToWorld(entity.position());
			// Rotate the entity's facing the same way the structure is rotated: turn its look vector,
			// then read back yaw/pitch (entities have no roll). Velocity is rotated too, so projectiles
			// keep heading the right way.
			Vec3 look = ContraptionMath.rotate(Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()),
				t.angle(), t.axis());
			double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
			float yaw = Mth.wrapDegrees((float) (Mth.atan2(look.z, look.x) * RAD_TO_DEG) - 90.0F);
			float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(look.y, horizontal) * RAD_TO_DEG)));
			entity.setDeltaMovement(ContraptionMath.rotate(entity.getDeltaMovement(), t.angle(), t.axis()));
			// moveTo also calls setOldPosAndRot, snapping the interpolation history (xo/xOld/yRotO/…) to
			// the world pose so the client doesn't lerp in from the contraption-local origin (a visible streak).
			entity.moveTo(world.x, world.y, world.z, yaw, pitch);
		} else {
			// No pose (e.g. tests): still snap the history to the spawn pose so there's no lerp from (0,0,0).
			entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
		}
		return realLevel.addFreshEntity(entity);
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
		// A block entity raises this on a discrete visual change (sign text, smoker lit, …). Refresh
		// that cell's client render NBT from the live BE and request a single whole-contraption resync.
		BlockEntity be = blockEntities.get(pos);
		if (be != null) {
			StructureBlockInfo info = contraption.getBlocks().get(pos);
			if (info != null)
				contraption.putBlock(pos, info.state(), info.nbt(), be.getUpdateTag(registryAccess()));
		}
		if (onNeedsSync != null)
			onNeedsSync.run();
	}

	@Override
	public void blockEntityChanged(BlockPos pos) {
		// Fires every hopper/furnace tick: only mark the host dirty for persistence; do NOT resync to
		// clients here (sendBlockUpdated handles discrete visual changes; flush handles save/sync).
		if (onChanged != null && blockEntities.containsKey(pos))
			onChanged.run();
	}

	/**
	 * Blocks emit sounds at their (local) coordinates; rotate them into world space so a button
	 * click, door creak, etc. plays where the block actually is on the contraption. All the
	 * positional {@code playSound} overloads (BlockPos / no-seed) funnel through this one.
	 *
	 * <p>The sound is emitted on the <b>real</b> level: {@link WrappedServerLevel} stubs
	 * {@code playSound} to a no-op (catnip suppresses sim-level sounds), so calling {@code super}
	 * would drop it. We pass a {@code null} source player so the interacting player hears it too —
	 * contraption interactions aren't predicted client-side, so nothing plays it locally.
	 */
	@Override
	public void playSound(@Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource source,
		float volume, float pitch) {
		Vec3 world = transform != null ? transform.get().localToWorld(new Vec3(x, y, z)) : new Vec3(x, y, z);
		realLevel.playSound(null, world.x, world.y, world.z, sound, source, volume, pitch);
	}

	/**
	 * Entity-attached sounds: the entity already lives in world space, so no transform is needed —
	 * just emit on the real level ({@code super} is a no-op as above).
	 */
	@Override
	public void playSound(@Nullable Player player, Entity entity, SoundEvent sound, SoundSource source, float volume,
		float pitch) {
		realLevel.playSound(null, entity, sound, source, volume, pitch);
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
