package g_mungus.zps.blockentity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import g_mungus.zps.client.renderer.contraption.ContraptionRenderState;
import g_mungus.zps.contraption.AssemblyException;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionPlaceContext;
import g_mungus.zps.contraption.ContraptionRotationState;
import g_mungus.zps.contraption.ContraptionSimLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import g_mungus.zps.contraption.StructureTransform;
import g_mungus.zps.contraption.collision.ContraptionCollider;
import g_mungus.zps.contraption.util.ContraptionMath;
import g_mungus.zps.mixin.ServerGamePacketListenerImplAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
	private boolean assembleNextTick;
	private boolean wasPowered;
	private Axis rotationAxis = Axis.Y;

	private float angle;
	private float prevAngle;

	/** Client-only cache of reconstructed block entities for rendering. */
	@Nullable
	private ContraptionRenderState renderState;

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

	public void requestToggle() {
		assembleNextTick = true;
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

		boolean powered = level.hasNeighborSignal(worldPosition);
		if (powered != wasPowered) {
			wasPowered = powered;
			assembleNextTick = true;
		}

		if (assembleNextTick) {
			assembleNextTick = false;
			if (running)
				disassemble();
			else
				assemble();
		}

		if (!running || contraption == null)
			return;

		angle = (angle + DEGREES_PER_TICK) % 360;
		// Resolve non-player entities here; players are resolved client-side.
		collide(entity -> !(entity instanceof Player));
		// Riders stand on blocks that aren't in the world, so keep the server from
		// kicking them for "floating".
		keepRidersAfloat();
	}

	public void clientTick() {
		prevAngle = angle;
		if (!running || contraption == null) {
			ACTIVE_CLIENT.remove(this);
			return;
		}
		ACTIVE_CLIENT.add(this);

		angle = (angle + DEGREES_PER_TICK) % 360;
		// Local-player collision is driven from ContraptionInteractionClient (client-only)
		// so this class never references client types and stays dist-safe.
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ACTIVE_CLIENT.remove(this);
	}

	// endregion

	// region assembly

	public void assemble() {
		if (level == null || level.isClientSide)
			return;

		BlockPos anchor = worldPosition.relative(getFacing());
		Contraption next = new Contraption();
		try {
			next.assemble(level, anchor, worldPosition);
		} catch (AssemblyException e) {
			return;
		}

		next.removeBlocksFromWorld(level);
		contraption = next;
		rotationAxis = getFacing().getAxis();
		running = true;
		angle = 0;
		prevAngle = 0;
		setChanged();
		sendData();
	}

	public void disassemble() {
		if (level == null || level.isClientSide || !running || contraption == null)
			return;

		BlockPos anchor = worldPosition.relative(getFacing());
		StructureTransform transform = new StructureTransform(anchor, rotationAxis, angle);
		contraption.addBlocksToWorld(level, transform);

		contraption = null;
		running = false;
		angle = 0;
		prevAngle = 0;
		setChanged();
		sendData();
	}

	/** Called from the block when the motor is broken so captured blocks aren't lost. */
	public void onMotorRemoved() {
		if (running && contraption != null)
			disassemble();
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

		BlockState state = info.state();
		BlockPos worldPos = BlockPos.containing(transform.localBlockCenterToWorld(local));
		ItemStack tool = player.getMainHandItem();

		if (!player.isCreative() && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
			BlockEntity be = null;
			if (info.nbt() != null && state.getBlock() instanceof EntityBlock entityBlock) {
				be = entityBlock.newBlockEntity(worldPos, state);
				if (be != null) {
					be.setLevel(serverLevel);
					be.loadWithComponents(info.nbt(), serverLevel.registryAccess());
				}
			}
			List<ItemStack> drops = Block.getDrops(state, serverLevel, worldPos, be, player, tool);
			for (ItemStack drop : drops)
				Block.popResource(serverLevel, worldPos, drop);
			state.spawnAfterBreak(serverLevel, worldPos, tool, true);
		}

		// Break particles + sound (vanilla level event 2001).
		serverLevel.levelEvent(2001, worldPos, Block.getId(state));
		contraption.removeBlock(local);
		setChanged();
		sendData();
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

		// Read-only overlay exposing the contraption's blocks at their local positions, so
		// getStateForPlacement sees in-structure neighbours (fences/walls/redstone/stairs).
		ContraptionSimLevel sim = new ContraptionSimLevel(serverLevel, contraption);
		ContraptionPlaceContext.Placed placed =
			ContraptionPlaceContext.resolve(sim, player, hand, stack, local, localFace, localHit, transform);
		if (placed == null)
			return false;

		BlockPos placePos = placed.pos();
		BlockState placeState = placed.state();

		CompoundTag beNbt = null;
		CompoundTag updateTag = null;
		if (placeState.getBlock() instanceof EntityBlock entityBlock) {
			BlockEntity be = entityBlock.newBlockEntity(placePos, placeState);
			if (be != null) {
				be.setLevel(serverLevel);
				beNbt = be.saveWithFullMetadata(serverLevel.registryAccess());
				updateTag = be.getUpdateTag(serverLevel.registryAccess());
			}
		}

		contraption.putBlock(placePos, placeState, beNbt, updateTag);
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

	// endregion

	// region collision

	private void collide(Predicate<Entity> shouldCollide) {
		Vec3 anchorVec = Vec3.atLowerCornerOf(worldPosition.relative(getFacing()));
		ContraptionRotationState rotation = new ContraptionRotationState(rotationAxis, angle);
		AABB worldBounds = computeWorldBounds(anchorVec);
		ContraptionCollider.collideEntities(level, anchorVec, rotation, contraption, worldBounds,
			this::getContactPointMotion, shouldCollide);
	}

	/**
	 * Resolve a single player against this contraption (their movement is
	 * client-authoritative, so the client drives this for its local player). Kept
	 * free of any client-only type so this BlockEntity class stays dist-safe.
	 */
	public void collideWithPlayer(Player player) {
		if (!running || contraption == null || player == null)
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
		Vec3 rel = worldPos.subtract(Vec3.atLowerCornerOf(worldPosition.relative(getFacing())))
			.subtract(ContraptionMath.CENTER_OF_ORIGIN);
		float angleDelta = angle - prevAngle;
		return rel.subtract(ContraptionMath.rotate(rel, -angleDelta, rotationAxis));
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
		tag.putBoolean("WasPowered", wasPowered);
		tag.putInt("Axis", rotationAxis.ordinal());
		tag.putFloat("Angle", angle);
		if (contraption != null)
			tag.put("Contraption", contraption.writeNBT());
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
		wasPowered = tag.getBoolean("WasPowered");
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
			contraption.readNBT(registries, tag.getCompound("Contraption"));
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
