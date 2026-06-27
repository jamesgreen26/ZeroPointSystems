package g_mungus.zps.blockentity;

import javax.annotation.Nullable;

import g_mungus.zps.contraption.AssemblyException;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionRotationState;
import g_mungus.zps.contraption.StructureTransform;
import g_mungus.zps.contraption.collision.ContraptionCollider;
import g_mungus.zps.contraption.util.ContraptionMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

	@Nullable
	private Contraption contraption;
	private boolean running;
	private boolean assembleNextTick;
	private boolean wasPowered;
	private Axis rotationAxis = Axis.Y;

	private float angle;
	private float prevAngle;

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

	public boolean isRunning() {
		return running;
	}

	public void requestToggle() {
		assembleNextTick = true;
	}

	public float getInterpolatedAngle(float partialTick) {
		return ContraptionMath.angleLerp(partialTick, prevAngle, angle);
	}

	public Axis getRotationAxis() {
		return rotationAxis;
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
		collideEntities();
	}

	public void clientTick() {
		prevAngle = angle;
		if (running)
			angle = (angle + DEGREES_PER_TICK) % 360;
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

	// region collision

	private void collideEntities() {
		Vec3 anchorVec = Vec3.atLowerCornerOf(worldPosition.relative(getFacing()));
		ContraptionRotationState rotation = new ContraptionRotationState(rotationAxis, angle);
		AABB worldBounds = computeWorldBounds(anchorVec);
		ContraptionCollider.collideEntities(level, anchorVec, rotation, contraption, worldBounds,
			this::getContactPointMotion);
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
		running = tag.getBoolean("Running");
		wasPowered = tag.getBoolean("WasPowered");
		rotationAxis = Axis.values()[tag.getInt("Axis")];
		angle = tag.getFloat("Angle");
		prevAngle = angle;
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
