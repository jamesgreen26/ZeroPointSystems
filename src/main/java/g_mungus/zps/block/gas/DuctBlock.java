package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.DuctGeometry;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNetworkComponent;
import g_mungus.zps.block.gas.core.KNodeBlockImpl;
import g_mungus.zps.block.gas.core.LeakyPipeDuctNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.DuctBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;


/**
 * Carries gas between nodes on the Kelvin network. Accepts a connection on every face and imposes
 * no behaviour of its own, so whatever a neighbour proposes — a pump, a valve — defines the edge.
 *
 * <p>Each face is {@code NONE}, {@code CONNECTION} or {@code LEAK}. A leak is what is left when a
 * neighbour is blown up: the pipe end stays open and bleeds gas into the world until something is
 * put back against it.
 */
public class DuctBlock extends KNodeBlockImpl
        implements EntityBlock, GasNetworkComponent, LeakyPipeDuctNode.DuctBlockFaces {

    /** Bore of a duct, in metres; Kelvin's own default pipe radius. */
    private static final double RADIUS = 0.125;
    /** This block's share of an edge's length: half a block. */
    private static final double HALF_LENGTH = 0.25;

    private static final double VOLUME = 1.0;
    private static final double MAX_PRESSURE = 16_375_049.0;
    private static final double MAX_TEMPERATURE = 1478.0;
    private static final double HEAT_CONDUCTIVITY = 1687.5;
    private static final double HEAT_CAPACITY = 449.0;

    public DuctBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<DuctConnectionType> property : DuctGeometry.connectionProperties()) {
            state = state.setValue(property, DuctConnectionType.NONE);
        }
        registerDefaultState(state);
    }

    private static EnumProperty<DuctConnectionType> propertyFor(Direction direction) {
        return DuctGeometry.connectionProperty(direction);
    }

    @Override
    public DuctConnectionType connectionAt(BlockState state, Direction direction) {
        return state.getValue(propertyFor(direction));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        DuctGeometry.connectionProperties().forEach(builder::add);
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new LeakyPipeDuctNode(pos, VOLUME, MAX_PRESSURE, MAX_TEMPERATURE,
                HEAT_CONDUCTIVITY, HEAT_CAPACITY);
    }

    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
    }

    // --- connection state -------------------------------------------------------------------

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            DuctConnectionType connection;
            if (GasEdgeNegotiator.wouldConnect(level, pos, direction)) {
                connection = DuctConnectionType.CONNECTION;
            } else if (state.getValue(propertyFor(direction)) == DuctConnectionType.LEAK) {
                // An open end stays open until something is put back against it.
                connection = DuctConnectionType.LEAK;
            } else {
                connection = DuctConnectionType.NONE;
            }
            result = result.setValue(propertyFor(direction), connection);
        }
        return result.equals(state) ? null : result;
    }

    /**
     * Blowing up a duct leaves the pipes around it open to the air, rather than neatly sealed.
     *
     * <p>Done here rather than in {@code onExplosionHit} because that fires while this block is
     * still standing: the neighbour would see a live connection, recompute the face, and throw the
     * mark away. By the time this runs the block is already air.
     */
    @Override
    public void wasExploded(@NotNull Level level, @NotNull BlockPos pos, @NotNull Explosion explosion) {
        super.wasExploded(level, pos, explosion);

        if (level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof DuctBlock)) {
                continue;
            }
            EnumProperty<DuctConnectionType> property = propertyFor(direction.getOpposite());
            if (neighborState.getValue(property) != DuctConnectionType.LEAK) {
                level.setBlock(neighborPos,
                        neighborState.setValue(property, DuctConnectionType.LEAK), Block.UPDATE_ALL);
            }
        }
    }

    // --- shape ------------------------------------------------------------------------------

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape shape = DuctGeometry.core(DuctGeometry.hasHorizontalConnection(state));
        for (Direction direction : Direction.values()) {
            if (state.getValue(propertyFor(direction)) != DuctConnectionType.NONE) {
                shape = Shapes.joinUnoptimized(shape, DuctGeometry.arm(direction), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return ModBlockEntities.DUCT.get().create(blockPos, blockState);
    }

    /**
     * Ducts only tick while they are leaking, which is when they have a plume to draw and a rate to
     * report. An intact duct costs nothing: the ticker is re-evaluated whenever the block state
     * changes, so it attaches the moment a face opens and detaches when it is sealed again.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (type != ModBlockEntities.DUCT.get() || LeakyPipeDuctNode.countLeakingFaces(state) == 0) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((DuctBlockEntity) blockEntity).tick();
    }
}
