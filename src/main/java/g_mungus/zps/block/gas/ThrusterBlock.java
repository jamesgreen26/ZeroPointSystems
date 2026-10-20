package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.ThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Burns Stellane fed in from behind and pushes the grid it is on away from its nozzle. See
 * {@link ThrusterBlockEntity} for the burn.
 *
 * <p>{@link #FACING} is the nozzle, the side the exhaust leaves by; the single face opposite it is
 * the inlet. The redstone signal strength, held in {@link #POWER}, is the throttle: each step asks
 * for a fifteenth more gas.
 */
public class ThrusterBlock extends GasNodeBlock implements EntityBlock {

    /** The direction the exhaust leaves by. The network joins on the opposite face, and nowhere else. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** The throttle: the strongest redstone signal reaching the block. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** A short combustion chamber, in cubic metres. */
    public static final double VOLUME = 0.25;
    public static final double MAX_PRESSURE = 16_375_049.0;
    public static final double MAX_TEMPERATURE = 1478.0;

    /**
     * Follows the model rather than filling the cube. With a full-cube shape, flat lighting lights every
     * face from the neighbour it points at, so faces turned toward a solid block render black.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, Shapes.or(
                    box(facing, 0, 16, 2, 14),
                    box(facing, 2, 14, 0, 4),
                    box(facing, 2, 14, 14, 16)));
        }
    }

    /** A box spanning {@code [lo, hi]} across the axis and {@code [back, front]} along it, front at the nozzle. */
    private static VoxelShape box(Direction facing, double lo, double hi, double back, double front) {
        double near = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? back : 16 - front;
        double far = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? front : 16 - back;
        return switch (facing.getAxis()) {
            case X -> Block.box(near, lo, lo, far, hi, hi);
            case Y -> Block.box(lo, near, lo, hi, far, hi);
            case Z -> Block.box(lo, lo, near, hi, hi, far);
        };
    }

    public ThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        // Nozzle back toward the player, like a dispenser; sneaking turns it away.
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWER, context.getLevel().getBestNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);

        if (level.isClientSide()) {
            return;
        }
        int power = level.getBestNeighborSignal(pos);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new PipeDuctNode(pos, NodeBehaviorType.PIPE, new HashSet<>(),
                VOLUME, MAX_PRESSURE, MAX_TEMPERATURE, 449.0);
    }

    /** Joins on the inlet face alone, behind the nozzle. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(FACING) || toNeighbor != state.getValue(FACING).getOpposite()) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
    }

    /** Nothing about the shape depends on the neighbours, so there is never a state to take. */
    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    // --- shape ------------------------------------------------------------------------------

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Sturdy on every face despite the recessed shape, so blocks can still be placed against it. */
    @Override
    public @NotNull VoxelShape getBlockSupportShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                       @NotNull BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.THRUSTER.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return type != ModBlockEntities.THRUSTER.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((ThrusterBlockEntity) blockEntity).tick();
    }
}
