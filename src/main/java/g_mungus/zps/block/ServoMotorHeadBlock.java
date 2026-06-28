package g_mungus.zps.block;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import g_mungus.zps.contraption.ContraptionBlockGetter;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * The rotating "head" of a Servo Motor, analogous to the vanilla piston head: it
 * is unobtainable (no item, no loot) and only ever exists as a block inside a
 * {@link g_mungus.zps.blockentity.ServoMotorBlockEntity}-hosted contraption,
 * seeded at the motor's own cell. Breaking it is routed to breaking the whole
 * motor, and it gets slower to break the larger the contraption is.
 */
public class ServoMotorHeadBlock extends Block {

	public static final MapCodec<ServoMotorHeadBlock> CODEC = simpleCodec(ServoMotorHeadBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	/** The head fills the front 1/4 of the cell at the facing end (the motor takes the back 3/4). */
	private static final Map<Direction, VoxelShape> SHAPES = Util.make(new EnumMap<>(Direction.class), m -> {
		m.put(Direction.DOWN, Block.box(0, 0, 0, 16, 4, 16));
		m.put(Direction.UP, Block.box(0, 12, 0, 16, 16, 16));
		m.put(Direction.NORTH, Block.box(0, 0, 0, 16, 16, 4));
		m.put(Direction.SOUTH, Block.box(0, 0, 12, 16, 16, 16));
		m.put(Direction.WEST, Block.box(0, 0, 0, 4, 16, 16));
		m.put(Direction.EAST, Block.box(12, 0, 0, 16, 16, 16));
	});

	public ServoMotorHeadBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected @NotNull MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
		builder.add(FACING);
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
	public float getDestroyProgress(BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
		float base = super.getDestroyProgress(state, player, level, pos);
		// When mined out of a contraption the getter exposes the structure, so the head
		// breaks at the same scaled rate as the coinciding motor block.
		int count = level instanceof ContraptionBlockGetter getter ? getter.contraption().getBlocks().size() : 1;
		return ServoMotorBlock.scaledDestroyProgress(base, count);
	}
}
