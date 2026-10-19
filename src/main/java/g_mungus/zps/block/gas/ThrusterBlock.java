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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.HashSet;

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
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
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
