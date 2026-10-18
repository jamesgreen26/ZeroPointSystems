package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.AirCompressorBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode;

import java.util.HashSet;

/**
 * Draws gas in from the world and pushes it into the network. See {@link AirCompressorBlockEntity}
 * for how.
 *
 * <p>The fan on top is the intake: the compressor draws from the space above it. {@link #FACING} is
 * the outlet, a check valve pointing away from the machine like the Vaporizer's, and nothing joins
 * the network on any other face. A redstone signal shuts it.
 */
public class AirCompressorBlock extends GasNodeBlock implements EntityBlock {

    /** The outlet face. Always horizontal: the intake is the top. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The intake face. */
    public static final Direction INTAKE = Direction.UP;
    /** Shut while powered. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Drew gas in on its last tick. Only for the model. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** The receiver's internal volume, in cubic metres. */
    public static final double VOLUME = 0.5;
    /** The same ceilings as a duct: filling past what the line can take gains nothing. */
    public static final double MAX_PRESSURE = 16_375_049.0;
    public static final double MAX_TEMPERATURE = 1478.0;
    private static final double HEAT_CAPACITY = 449.0;

    public AirCompressorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWERED, RUNNING);
    }

    /** The outlet faces the player, like a furnace's front; sneaking turns it away. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction away = context.getHorizontalDirection();
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? away
                : away.getOpposite();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);

        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    /** Whether a redstone signal is holding it off. */
    public static boolean isShut(BlockState state) {
        return state.hasProperty(POWERED) && state.getValue(POWERED);
    }

    /** The face gas leaves by. */
    public static Direction outlet(BlockState state) {
        return state.getValue(FACING);
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME,
                MAX_PRESSURE, MAX_TEMPERATURE, HEAT_CAPACITY, 1.0);
    }

    /** Joins on the outlet face alone, and only ever carries gas away from the machine. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(FACING) || toNeighbor != outlet(state)) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH)
                .with(new OneWayFacet(toNeighbor));
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // --- block entity -----------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.AIR_COMPRESSOR.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.AIR_COMPRESSOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((AirCompressorBlockEntity) blockEntity).serverTick();
    }
}
