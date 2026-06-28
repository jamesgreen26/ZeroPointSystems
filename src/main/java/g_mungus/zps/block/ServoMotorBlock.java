package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bearing-style driver block: assembles connected blocks in front of it into a
 * {@link ServoMotorBlockEntity}-hosted contraption and spins them around its
 * facing axis.
 */
public class ServoMotorBlock extends BaseEntityBlock {

	public static final MapCodec<ServoMotorBlock> CODEC = simpleCodec(ServoMotorBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	/** The motor occupies the back 3/4 of the cell; the head fills the front 1/4 at the facing end. */
	private static final Map<Direction, VoxelShape> SHAPES = Util.make(new EnumMap<>(Direction.class), m -> {
		m.put(Direction.DOWN, Block.box(0, 4, 0, 16, 16, 16));
		m.put(Direction.UP, Block.box(0, 0, 0, 16, 12, 16));
		m.put(Direction.NORTH, Block.box(0, 0, 4, 16, 16, 16));
		m.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 12));
		m.put(Direction.WEST, Block.box(4, 0, 0, 16, 16, 16));
		m.put(Direction.EAST, Block.box(0, 0, 0, 12, 16, 16));
	});

	/** Upper bound on how much the attached contraption can slow breaking the motor/head. */
	public static final float MAX_BREAK_MULTIPLIER = 10f;

	/**
	 * Dampen the per-tick break progress by the contraption size: bigger structures break
	 * slower, but with diminishing growth and a hard cap so they stay breakable.
	 */
	public static float scaledDestroyProgress(float base, int count) {
		if (count <= 1)
			return base;
		double multiplier = Math.min(MAX_BREAK_MULTIPLIER, 1.0 + Math.log(count) / Math.log(2));
		return (float) (base / multiplier);
	}

	public ServoMotorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @NotNull BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	@Override
	protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}

	@Override
	public @NotNull RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
		@NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected @NotNull VoxelShape getCollisionShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
		@NotNull CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
		@NotNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		// Place pre-assembled: seed the contraption with the head straight away.
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServoMotorBlockEntity motor)
			motor.initContraption();
	}

	@Override
	public float getDestroyProgress(BlockState state, @NotNull Player player, @NotNull BlockGetter level,
		@NotNull BlockPos pos) {
		float base = super.getDestroyProgress(state, player, level, pos);
		int count = level.getBlockEntity(pos) instanceof ServoMotorBlockEntity motor
			? motor.getContraptionBlockCount() : 1;
		return scaledDestroyProgress(base, count);
	}

	@Override
	public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState,
		boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof ServoMotorBlockEntity motor)
				motor.onMotorRemoved();
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return ModBlockEntities.SERVO_MOTOR.get().create(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
		@NotNull BlockEntityType<T> type) {
		if (type != ModBlockEntities.SERVO_MOTOR.get())
			return null;
		return (lvl, pos, st, be) -> {
			if (be instanceof ServoMotorBlockEntity motor) {
				if (lvl.isClientSide)
					motor.clientTick();
				else
					motor.serverTick();
			}
		};
	}
}
