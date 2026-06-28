package g_mungus.zps.blockentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.ServoMotorHeadBlock;
import g_mungus.zps.client.renderer.contraption.ContraptionRenderState;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionBlockGetter;
import g_mungus.zps.contraption.ContraptionPlacementUtil;
import g_mungus.zps.contraption.ContraptionPlaceContext;
import g_mungus.zps.contraption.ContraptionRotatedEntity;
import g_mungus.zps.contraption.ContraptionRotationState;
import g_mungus.zps.contraption.ContraptionSimServerLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import g_mungus.zps.contraption.collision.ContraptionCollider;
import g_mungus.zps.contraption.util.ContraptionMath;
import g_mungus.zps.mixin.FallingBlockEntityInvoker;
import g_mungus.zps.mixin.ServerGamePacketListenerImplAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hosts a {@link Contraption} directly (no separate entity). On toggle it
 * assembles the connected blocks in front of it, spins them around the block's
 * facing axis, and writes them back to the world on disassembly. Behaves like
 * Create's Mechanical Bearing, but the structure lives in this BlockEntity.
 */
public class ServoMotorBlockEntity extends BlockEntity {

	/** Rotation speed in degrees per tick (~16 RPM at 2°/t). */
	public static final float DEGREES_PER_TICK = 2f;

	/** Client-side set of currently-assembled motors, for interaction raytracing. */
	public static final Set<ServoMotorBlockEntity> ACTIVE_CLIENT = ConcurrentHashMap.newKeySet();

	/** Max squared distance from a player to a contraption block for break/place. */
	private static final double INTERACT_RANGE_SQR = 7.0 * 7.0;

	@Nullable
	private Contraption contraption;
	private boolean running;
	/** Set while we tear the motor down ourselves so {@link #onMotorRemoved} doesn't double-drop. */
	private boolean suppressRemovalDrops;
	private Axis rotationAxis = Axis.Y;

	private float angle;
	private float prevAngle;

	/** Client-only cache of reconstructed block entities for rendering. */
	@Nullable
	private ContraptionRenderState renderState;

	/** Server-only cached simulation level (see {@link #simLevel()}); rebuilt when the contraption changes. */
	@Nullable
	private ContraptionSimServerLevel simLevel;

	/** Set when a scheduled block tick mutates the structure, so {@link #serverTick()} re-syncs once. */
	private boolean simNeedsSync;

	public ServoMotorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SERVO_MOTOR.get(), pos, state);
	}

	public Direction getFacing() {
		BlockState state = getBlockState();
		return state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING)
			: state.hasProperty(HorizontalDirectionalBlock.FACING)
				? state.getValue(HorizontalDirectionalBlock.FACING)
				: Direction.NORTH;
	}

	@Nullable
	public Contraption getContraption() {
		return contraption;
	}

	/**
	 * Client-only: replace the contraption with a predicted version (after a local
	 * break/place) so it renders/collides immediately, before the authoritative
	 * server sync arrives and replaces it. A fresh instance triggers the renderer
	 * and render-state caches to rebuild (they key on identity).
	 */
	public void setContraptionClient(Contraption predicted) {
		if (level != null && level.isClientSide)
			contraption = predicted;
	}

	public boolean isRunning() {
		return running;
	}

	/** Number of blocks in the contraption (head included), at least 1, for break-speed scaling. */
	public int getContraptionBlockCount() {
		return contraption == null ? 1 : Math.max(1, contraption.getBlocks().size());
	}

	/** Local position of the head block: the motor's own cell, relative to the anchor in front. */
	public BlockPos headLocalPos() {
		return BlockPos.ZERO.relative(getFacing().getOpposite());
	}

	public float getInterpolatedAngle(float partialTick) {
		return ContraptionMath.angleLerp(partialTick, prevAngle, angle);
	}

	/** Raw current rotation angle in degrees (server-authoritative). */
	public float getAngle() {
		return angle;
	}

	public Axis getRotationAxis() {
		return rotationAxis;
	}

	/**
	 * Client-only: reconstructed block entities of the captured structure, rebuilt
	 * when the contraption changes. Returns null when nothing is assembled.
	 */
	@Nullable
	public ContraptionRenderState getRenderState() {
		if (level == null || contraption == null) {
			renderState = null;
			return null;
		}
		if (renderState == null || renderState.getContraption() != contraption)
			renderState = new ContraptionRenderState(level, contraption);
		return renderState;
	}

	// region ticking

	public void serverTick() {
		prevAngle = angle;

		// Pre-assembled on placement; lazily seed the head for blocks created another way
		// (e.g. /setblock) so the contraption always exists.
		if (contraption == null)
			initContraption();

		// Spins only while powered; the contraption is permanent either way.
		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered != running) {
			running = powered;
			setChanged();
			sendData();
		}

		if (contraption == null)
			return;

		// Drive the contraption's own block-tick queue (falling blocks, and redstone components
		// like repeaters/observers/button-release), kept separate from the outer world's tick queue.
		tickContraptionBlocks();
		if (simNeedsSync) {
			simNeedsSync = false;
			sendData();
		}

		if (running)
			angle = (angle + DEGREES_PER_TICK) % 360;
		// Resolve non-player entities here (players are resolved client-side). A stopped
		// contraption is still solid; only the carry motion goes to zero.
		collide(entity -> !(entity instanceof Player));
		// Riders stand on blocks that aren't in the world, so keep the server from
		// kicking them for "floating".
		keepRidersAfloat();
	}

	public void clientTick() {
		prevAngle = angle;
		// Targeting/building/breaking work even while stopped, so track any motor that has a
		// contraption — not just spinning ones.
		if (contraption == null) {
			ACTIVE_CLIENT.remove(this);
			return;
		}
		ACTIVE_CLIENT.add(this);

		if (running)
			angle = (angle + DEGREES_PER_TICK) % 360;
		// Local-player collision is driven from ContraptionInteractionClient (client-only)
		// so this class never references client types and stays dist-safe.

		// Most entities interpolate toward their server-synced position on the client
		// (Entity#baseTick), so the server's authoritative collision result is what the
		// client renders — smooth. FallingBlockEntity is the exception: its tick runs raw
		// physics every tick and never calls super.tick()/interpolates, so on the client it
		// free-falls straight through the contraption (whose blocks aren't real world blocks)
		// and only snaps back when a position packet lands — which reads as jitter. Resolve
		// it here too so the client prediction stays glued to the platform, matching the
		// server. (Create gets this for free: its contraption is an entity that runs
		// collideEntities on both sides.)
		collide(entity -> entity instanceof FallingBlockEntity || entity instanceof ItemEntity);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ACTIVE_CLIENT.remove(this);
	}

	/**
	 * The server simulation level bound to this motor's contraption: writes/ticks mutate the
	 * contraption and mark the BE dirty, and — because it is a real {@link ServerLevel}
	 * ({@link ContraptionSimServerLevel}) — scheduled ticks and redstone run natively. Callers batch
	 * a single {@link #sendData()} after an operation, so a multi-block change (e.g. a structural
	 * collapse) is one network re-sync. Cached (constructing it builds a full ServerLevel) and
	 * rebuilt when the contraption identity changes. Server-only.
	 */
	private ContraptionSimServerLevel simLevel() {
		if (simLevel == null || simLevel.getContraption() != contraption)
			simLevel = new ContraptionSimServerLevel((ServerLevel) level, contraption, this::setChanged);
		return simLevel;
	}

	/** Run the contraption's own block-tick queue once (server-side). */
	private void tickContraptionBlocks() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		ContraptionSimServerLevel sim = simLevel();
		contraption.getBlockTicks().tick(serverLevel.getGameTime(), 65536,
			(local, block) -> onContraptionBlockTick(local, block, sim, serverLevel));
	}

	/**
	 * Ticker callback for a due scheduled block tick: re-validate the cell against the scheduled
	 * block, run the block's own tick against the simulation level (so repeaters/comparators/
	 * observers/redstone-torches/button-release behave as in a normal world), then handle falling
	 * blocks. The {@code state.tick} call is legal because {@code sim} is a real ServerLevel.
	 */
	private void onContraptionBlockTick(BlockPos local, Block block, ContraptionSimServerLevel sim,
		ServerLevel serverLevel) {
		StructureBlockInfo info = contraption.getBlocks().get(local);
		if (info == null || !info.state().is(block))
			return;
		info.state().tick(sim, local, serverLevel.getRandom());
		// A redstone tick (repeater/button-release/observer/…) likely changed the structure; re-sync.
		simNeedsSync = true;
		// Re-read: the tick above may have changed or removed the cell.
		StructureBlockInfo after = contraption.getBlocks().get(local);
		if (after != null && after.state().is(block) && block instanceof FallingBlock)
			detachFallingBlock(local, after.state());
	}

	/**
	 * If the falling block at {@code local} has no support directly below it (in local
	 * space), detach it from the structure (spawning a real {@link FallingBlockEntity}) and
	 * run a structural update so the change propagates: it falls in the world and lands on any
	 * lower platform via the existing falling-block collision in {@link #serverTick()}.
	 */
	private void detachFallingBlock(BlockPos local, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		StructureBlockInfo below = contraption.getBlocks().get(local.below());
		BlockState belowState = below == null ? Blocks.AIR.defaultBlockState() : below.state();
		if (!FallingBlock.isFree(belowState))
			return; // still supported

		ContraptionSimServerLevel sim = simLevel();
		detachBlock(local, state, averagePlatformVelocity(List.of(local)), sim, serverLevel);
		applyStructureUpdate(sim, serverLevel);
	}

	/**
	 * Remove the block at {@code local} through the simulation level — so the standard block update
	 * fires (e.g. a falling block above it reschedules its fall) — and spawn a real
	 * {@link FallingBlockEntity} for it at the block's current rotated world position, with the
	 * contraption's orientation and the given release {@code velocity}. Does NOT re-sync; callers
	 * batch one {@link #sendData()}.
	 */
	private void detachBlock(BlockPos local, BlockState state, Vec3 velocity, ContraptionSimServerLevel sim,
		ServerLevel serverLevel) {
		sim.setBlock(local, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

		ContraptionTransform transform = ContraptionTransform.ofCurrent(this);
		// Feet at the cell's bottom-centre: local x/z centred, y at the block's bottom face.
		Vec3 feet = transform.localToWorld(new Vec3(local.getX() + 0.5, local.getY(), local.getZ() + 0.5));
		FallingBlockEntity entity = FallingBlockEntityInvoker.zps$create(serverLevel, feet.x, feet.y, feet.z, state);
		// Inherit the contraption's current orientation so the block keeps it while falling.
		((ContraptionRotatedEntity) entity).zps$setContraptionRotation(transform.angle(), transform.axis());
		// Release it with the platform velocity (a spinning detach continues smoothly instead of
		// jerking to a stop); for a multi-block group this is the group average. Sent to clients in
		// the spawn packet's velocity.
		entity.setDeltaMovement(velocity);
		serverLevel.addFreshEntity(entity);
	}

	/**
	 * Structural update after a removal: collapse anything no longer connected to the head, then
	 * re-sync once. The falling-block re-evaluation is handled by the removal itself going through
	 * {@link ContraptionSimServerLevel#setBlock} (which runs the standard block update). Re-invoked when a
	 * falling block detaches, so collapses cascade.
	 */
	private void applyStructureUpdate(ContraptionSimServerLevel sim, ServerLevel serverLevel) {
		detachDisconnected(sim, serverLevel);
		setChanged();
		sendData();
	}

	/**
	 * Detach every block no longer connected to the head (26-way: face, edge or corner contact all
	 * keep a block attached) as a falling block entity. Each self-connected sub-group falls off as a
	 * cohesive unit: all its blocks inherit the group's average platform velocity, so the group
	 * translates together instead of shearing apart.
	 */
	private void detachDisconnected(ContraptionSimServerLevel sim, ServerLevel serverLevel) {
		var blocks = contraption.getBlocks();
		BlockPos head = headLocalPos();
		if (!blocks.containsKey(head))
			return;

		// Everything 26-connected to the head stays attached.
		Set<BlockPos> connected = new HashSet<>();
		connectedGroup(head, blocks.keySet(), connected);
		if (connected.size() == blocks.size())
			return;

		Set<BlockPos> disconnected = new HashSet<>();
		for (BlockPos p : blocks.keySet())
			if (!connected.contains(p))
				disconnected.add(p);

		Set<BlockPos> grouped = new HashSet<>();
		for (BlockPos start : disconnected) {
			List<BlockPos> group = connectedGroup(start, disconnected, grouped);
			if (group.isEmpty())
				continue; // already part of an earlier group
			Vec3 velocity = averagePlatformVelocity(group);
			for (BlockPos p : group) {
				StructureBlockInfo info = blocks.get(p);
				if (info == null)
					continue; // already removed by a cascading shape update
				detachBlock(p, info.state(), velocity, sim, serverLevel);
			}
		}
	}

	/** 26-way connected component of {@code start} within {@code domain}, accumulating into {@code visited}. */
	private static List<BlockPos> connectedGroup(BlockPos start, Set<BlockPos> domain, Set<BlockPos> visited) {
		List<BlockPos> group = new ArrayList<>();
		if (!domain.contains(start) || !visited.add(start))
			return group;
		Deque<BlockPos> stack = new ArrayDeque<>();
		group.add(start);
		stack.push(start);
		while (!stack.isEmpty()) {
			BlockPos p = stack.pop();
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0)
							continue;
						BlockPos n = p.offset(dx, dy, dz);
						if (domain.contains(n) && visited.add(n)) {
							group.add(n);
							stack.push(n);
						}
					}
		}
		return group;
	}

	/** Average platform velocity over a group of local cells, for releasing it as a cohesive unit. */
	private Vec3 averagePlatformVelocity(List<BlockPos> group) {
		ContraptionTransform transform = ContraptionTransform.ofCurrent(this);
		float spin = running ? DEGREES_PER_TICK : 0f;
		Vec3 sum = Vec3.ZERO;
		for (BlockPos p : group)
			sum = sum.add(platformVelocity(transform.localBlockCenterToWorld(p), spin));
		return sum.scale(1.0 / group.size());
	}

	// endregion

	// region assembly

	/**
	 * Seed the contraption with just the Servo Motor Head at the motor's own cell (on the
	 * rotation axis, so it spins in place). The rest of the contraption is built outward off
	 * the head via in-flight placement. The motor is never disassembled.
	 */
	public void initContraption() {
		if (level == null || level.isClientSide || contraption != null)
			return;

		Direction facing = getFacing();
		rotationAxis = facing.getAxis();
		BlockPos anchor = worldPosition.relative(facing);
		Contraption next = new Contraption();
		next.setAnchor(anchor);
		BlockState headState = ModBlocks.SERVO_MOTOR_HEAD.get().defaultBlockState()
			.setValue(ServoMotorHeadBlock.FACING, facing);
		next.putBlock(headLocalPos(), headState, null, null);

		contraption = next;
		angle = 0;
		prevAngle = 0;
		setChanged();
		sendData();
	}

	/** Called from the block when the motor is broken: drop every contraption block as items. */
	public void onMotorRemoved() {
		if (level == null || level.isClientSide)
			return;
		if (!suppressRemovalDrops && contraption != null)
			dropContraptionLoot(ItemStack.EMPTY);
		contraption = null;
		running = false;
	}

	// endregion

	// region in-flight editing (break / place)

	/** True if {@code player} is close enough to the rotated world position of a local block. */
	private boolean inReach(BlockPos local, ServerPlayer player, ContraptionTransform transform) {
		return player.position().distanceToSqr(transform.localBlockCenterToWorld(local)) <= INTERACT_RANGE_SQR;
	}

	/** Mine a single block out of the structure: drops, particles/sound, and structure update. */
	public void breakContraptionBlock(BlockPos local, ServerPlayer player) {
		if (level == null || level.isClientSide || contraption == null)
			return;
		StructureBlockInfo info = contraption.getBlocks().get(local);
		if (info == null)
			return;
		ServerLevel serverLevel = (ServerLevel) level;
		ContraptionTransform transform = ContraptionTransform.ofCurrent(this);
		if (!inReach(local, player, transform))
			return;

		// The head coincides with the in-world motor; breaking it tears down the whole motor.
		if (local.equals(headLocalPos())) {
			breakWholeMotor(player);
			return;
		}

		BlockState state = info.state();
		BlockPos worldPos = BlockPos.containing(transform.localBlockCenterToWorld(local));
		ItemStack tool = player.getMainHandItem();

		if (!player.isCreative() && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
			popBlockLoot(serverLevel, state, info.nbt(), worldPos, player, tool);
			state.spawnAfterBreak(serverLevel, worldPos, tool, true);
		}

		spawnContraptionBreakEffects(serverLevel, local, state, transform);
		// Remove through the sim level so neighbours get the standard block update (unsupported
		// falling blocks fall), then collapse anything no longer connected to the head. Re-syncs once.
		ContraptionSimServerLevel sim = simLevel();
		sim.setBlock(local, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		applyStructureUpdate(sim, serverLevel);
	}

	private void spawnContraptionBreakEffects(ServerLevel serverLevel, BlockPos local, BlockState state,
		ContraptionTransform transform) {
		if (contraption == null || state.isAir())
			return;

		Vec3 worldCenter = transform.localBlockCenterToWorld(local);
		SoundType sound = state.getSoundType();
		serverLevel.playSound(null, worldCenter.x, worldCenter.y, worldCenter.z, sound.getBreakSound(),
			SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);

		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
		VoxelShape shape = state.getShape(new ContraptionBlockGetter(contraption), local);
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			double sizeX = Math.min(1.0D, maxX - minX);
			double sizeY = Math.min(1.0D, maxY - minY);
			double sizeZ = Math.min(1.0D, maxZ - minZ);
			int countX = Math.max(2, Mth.ceil(sizeX / 0.25D));
			int countY = Math.max(2, Mth.ceil(sizeY / 0.25D));
			int countZ = Math.max(2, Mth.ceil(sizeZ / 0.25D));

			for (int x = 0; x < countX; ++x) {
				for (int y = 0; y < countY; ++y) {
					for (int z = 0; z < countZ; ++z) {
						double relX = ((double) x + 0.5D) / (double) countX;
						double relY = ((double) y + 0.5D) / (double) countY;
						double relZ = ((double) z + 0.5D) / (double) countZ;
						Vec3 localParticle = new Vec3(
							local.getX() + relX * sizeX + minX,
							local.getY() + relY * sizeY + minY,
							local.getZ() + relZ * sizeZ + minZ);
						Vec3 worldParticle = transform.localToWorld(localParticle);
						Vec3 velocity = ContraptionMath.rotate(new Vec3(relX - 0.5D, relY - 0.5D, relZ - 0.5D),
							transform.angle(), transform.axis());
						serverLevel.sendParticles(particle, worldParticle.x, worldParticle.y, worldParticle.z, 0,
							velocity.x, velocity.y, velocity.z, 1.0D);
					}
				}
			}
		});
	}

	/** Destroy the whole motor: drop the motor item and every contraption block, then remove it. */
	private void breakWholeMotor(ServerPlayer player) {
		ServerLevel serverLevel = (ServerLevel) level;
		if (serverLevel == null) return;
		serverLevel.levelEvent(2001, worldPosition, Block.getId(getBlockState()));
		serverLevel.removeBlock(worldPosition, false);
	}

	/** Drop the loot of every contraption block except the (unobtainable) head. */
	private void dropContraptionLoot(ItemStack tool) {
		if (level == null || level.isClientSide || contraption == null)
			return;
		ServerLevel serverLevel = (ServerLevel) level;
		if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
			return;

		BlockPos headLocal = headLocalPos();
		for (var entry : contraption.getBlocks().entrySet()) {
			if (entry.getKey().equals(headLocal))
				continue;
			popBlockLoot(serverLevel, entry.getValue().state(), entry.getValue().nbt(), worldPosition, null, tool);
		}
	}

	/** Reconstruct any block entity from {@code nbt} and pop the block's drops at {@code worldPos}. */
	private void popBlockLoot(ServerLevel serverLevel, BlockState state, @Nullable CompoundTag nbt, BlockPos worldPos,
		@Nullable ServerPlayer player, ItemStack tool) {
		BlockEntity be = null;
		if (nbt != null && state.getBlock() instanceof EntityBlock entityBlock) {
			be = entityBlock.newBlockEntity(worldPos, state);
			if (be != null) {
				be.setLevel(serverLevel);
				be.loadWithComponents(nbt, serverLevel.registryAccess());
			}
		}
		for (ItemStack drop : Block.getDrops(state, serverLevel, worldPos, be, player, tool))
			Block.popResource(serverLevel, worldPos, drop);
	}

	/** Place the player's held block into the structure with full vanilla placement context. */
	public boolean placeContraptionBlock(BlockPos local, Direction localFace, Vec3 localHit, ServerPlayer player,
		InteractionHand hand) {
		if (level == null || level.isClientSide || contraption == null)
			return false;
		if (!contraption.getBlocks().containsKey(local))
			return false;
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem))
			return false;

		ServerLevel serverLevel = (ServerLevel) level;
		ContraptionTransform transform = ContraptionTransform.ofCurrent(this);
		if (!inReach(local, player, transform))
			return false;

		// Overlay exposing the contraption's blocks at their local positions, so
		// getStateForPlacement sees in-structure neighbours (fences/walls/redstone/stairs)
		// and in-structure side-effects (FallingBlock#onPlace) read/write the contraption.
		ContraptionSimServerLevel sim = simLevel();
		ContraptionPlaceContext.Placed placed =
			ContraptionPlaceContext.resolve(sim, player, hand, stack, local, localFace, localHit, transform);
		if (placed == null)
			return false;

		BlockPos placePos = placed.pos();
		BlockState placeState = placed.state();
		if (!ContraptionPlacementUtil.isUnobstructed(serverLevel, sim, player, placePos, placeState, transform))
			return false;

		if (!ContraptionPlacementUtil.placeBlock(serverLevel, sim, contraption, player, stack, placed))
			return false;
		placeState = sim.getBlockState(placePos);
		// Placement side-effects (e.g. a FallingBlock scheduling its fall, redstone components
		// notifying neighbours) now run inside ContraptionSimServerLevel#setBlock via the block
		// lifecycle, so no explicit onPlace is needed here.
		if (!player.isCreative())
			stack.shrink(1);

		Vec3 worldCenter = transform.localBlockCenterToWorld(placePos);
		SoundType sound = placeState.getSoundType();
		serverLevel.playSound(null, BlockPos.containing(worldCenter), sound.getPlaceSound(), SoundSource.BLOCKS,
			(sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
		setChanged();
		sendData();
		return true;
	}

	/**
	 * Right-click a block in the structure (buttons, levers, doors, trapdoors, pressure plates, …).
	 * Runs the block's vanilla use logic against the server simulation level, so its side-effects —
	 * toggling {@code OPEN}/{@code POWERED}, scheduling the button-release tick, redstone neighbour
	 * updates, sounds — all happen against the contraption. Mirrors vanilla's right-click order
	 * (held-item block interaction first, then the block's own use), then re-syncs once.
	 */
	public boolean useContraptionBlock(BlockPos local, Direction localFace, Vec3 localHit, ServerPlayer player,
		InteractionHand hand) {
		if (level == null || level.isClientSide || contraption == null)
			return false;
		StructureBlockInfo info = contraption.getBlocks().get(local);
		if (info == null)
			return false;

		ContraptionTransform transform = ContraptionTransform.ofCurrent(this);
		if (!inReach(local, player, transform))
			return false;

		ContraptionSimServerLevel sim = simLevel();
		BlockState state = info.state();
		BlockHitResult hit = new BlockHitResult(localHit, localFace, local, false);
		ItemStack stack = player.getItemInHand(hand);

		if (!stack.isEmpty()) {
			ItemInteractionResult itemResult = state.useItemOn(stack, sim, player, hand, hit);
			if (itemResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
				if (itemResult.consumesAction()) {
					setChanged();
					sendData();
				}
				return itemResult.consumesAction();
			}
		}
		if (state.useWithoutItem(sim, player, hit).consumesAction()) {
			setChanged();
			sendData();
			return true;
		}
		return false;
	}

	// endregion

	// region collision

	private void collide(Predicate<Entity> shouldCollide) {
		Vec3 anchorVec = Vec3.atLowerCornerOf(worldPosition.relative(getFacing()));
		ContraptionRotationState rotation = new ContraptionRotationState(rotationAxis, angle);
		AABB worldBounds = computeWorldBounds(anchorVec);
		ContraptionCollider.collideEntities(level, anchorVec, rotation, contraption, worldBounds,
			this::getContactPointMotion, shouldCollide, this::captureLandedBlock);
	}

	/**
	 * Capture a {@link FallingBlockEntity} that has landed on the contraption into the
	 * structure at {@code localCell}, so it becomes part of the contraption (the inverse of
	 * {@link #detachFallingBlock}). Server-side; the collider only invokes this when the cell
	 * is empty and supported below.
	 */
	private void captureLandedBlock(Entity entity, BlockPos localCell) {
		if (contraption == null || !(level instanceof ServerLevel serverLevel)
			|| !(entity instanceof FallingBlockEntity falling))
			return;
		BlockState state = falling.getBlockState();
		if (state.isAir() || contraption.getBlocks().size() >= Contraption.MAX_BLOCKS)
			return;

		// Block-entity data from the falling block is dropped for now (sand/gravel/anvils have none).
		contraption.putBlock(localCell, state, null, null);
		falling.discard();

		Vec3 worldCenter = ContraptionTransform.ofCurrent(this).localBlockCenterToWorld(localCell);
		SoundType sound = state.getSoundType();
		serverLevel.playSound(null, BlockPos.containing(worldCenter), sound.getPlaceSound(), SoundSource.BLOCKS,
			(sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
		setChanged();
		sendData();
	}

	/**
	 * Resolve a single player against this contraption (their movement is
	 * client-authoritative, so the client drives this for its local player). Kept
	 * free of any client-only type so this BlockEntity class stays dist-safe.
	 */
	public void collideWithPlayer(Player player) {
		if (contraption == null || player == null)
			return;
		collide(entity -> entity == player);
	}

	private void keepRidersAfloat() {
		AABB bounds = computeWorldBounds(Vec3.atLowerCornerOf(worldPosition.relative(getFacing())));
		for (Player player : level.getEntitiesOfClass(Player.class, bounds)) {
			if (player instanceof ServerPlayer serverPlayer) {
				ServerGamePacketListenerImplAccessor connection =
					(ServerGamePacketListenerImplAccessor) serverPlayer.connection;
				connection.setAboveGroundTickCount(0);
				connection.setAboveGroundVehicleTickCount(0);
			}
		}
	}

	/** Velocity of the rotating platform at a world point, for carrying riders. */
	private Vec3 getContactPointMotion(Vec3 worldPos) {
		// Carry the rider to where this platform-fixed point will actually be after this
		// tick's rotation (re-apply the real rotation by +delta), so it lands exactly on
		// its circle. Using the previous tick's chord instead steps along the tangent each
		// tick and spirals the rider outward — Create maps through local space for the same
		// reason (toLocalVector(p,0) -> toGlobalVector(p,1)).
		return platformVelocity(worldPos, angle - prevAngle);
	}

	/**
	 * Platform velocity (blocks/tick) at a world point for an explicit per-tick rotation.
	 * Used to release a detached falling block with the motion it had on the platform, since
	 * at detach time (before {@code angle} is advanced this tick) {@code angle - prevAngle}
	 * is still 0.
	 */
	private Vec3 platformVelocity(Vec3 worldPos, float angleDeltaDeg) {
		Vec3 rel = worldPos.subtract(Vec3.atLowerCornerOf(worldPosition.relative(getFacing())))
			.subtract(ContraptionMath.CENTER_OF_ORIGIN);
		return ContraptionMath.rotate(rel, angleDeltaDeg, rotationAxis).subtract(rel);
	}

	private AABB computeWorldBounds(Vec3 anchorVec) {
		AABB local = contraption == null ? new AABB(BlockPos.ZERO) : contraption.getBounds();
		// Radius from the rotation center (anchor block center) to the farthest corner.
		double r = 0;
		double[] xs = { local.minX, local.maxX };
		double[] ys = { local.minY, local.maxY };
		double[] zs = { local.minZ, local.maxZ };
		for (double x : xs)
			for (double y : ys)
				for (double z : zs)
					r = Math.max(r, new Vec3(x, y, z).subtract(ContraptionMath.CENTER_OF_ORIGIN).length());
		Vec3 center = anchorVec.add(ContraptionMath.CENTER_OF_ORIGIN);
		return new AABB(center, center).inflate(r + 1);
	}

	// endregion

	// region sync + persistence

	private void sendData() {
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void writeState(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("Running", running);
		tag.putInt("Axis", rotationAxis.ordinal());
		tag.putFloat("Angle", angle);
		if (contraption != null)
			tag.put("Contraption", contraption.writeNBT(level == null ? 0L : level.getGameTime()));
	}

	private void readState(CompoundTag tag, HolderLookup.Provider registries) {
		// A structure edit (break/place) re-syncs the whole BE while it keeps spinning. The
		// client advances the angle deterministically every tick, so adopting the packet's
		// (already stale) angle here would snap the rotation. Only adopt the synced angle on
		// the initial load; otherwise keep the client's free-running angle.
		boolean wasRunningWithContraption = running && contraption != null;
		float clientAngle = angle;
		float clientPrevAngle = prevAngle;

		running = tag.getBoolean("Running");
		rotationAxis = Axis.values()[tag.getInt("Axis")];

		if (level != null && level.isClientSide && wasRunningWithContraption && running) {
			angle = clientAngle;
			prevAngle = clientPrevAngle;
		} else {
			angle = tag.getFloat("Angle");
			prevAngle = angle;
		}

		if (tag.contains("Contraption")) {
			contraption = new Contraption();
			contraption.readNBT(registries, tag.getCompound("Contraption"), level == null ? 0L : level.getGameTime());
		} else {
			contraption = null;
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		writeState(tag, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		readState(tag, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		writeState(tag, registries);
		return tag;
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		if (pkt != null && pkt.getTag() != null)
			handleUpdateTag(pkt.getTag(), registries);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		readState(tag, registries);
	}

	public AABB getRenderBoundingBox() {
		if (contraption == null)
			return new AABB(worldPosition);
		return computeWorldBounds(Vec3.atLowerCornerOf(worldPosition.relative(getFacing())));
	}

	// endregion
}
