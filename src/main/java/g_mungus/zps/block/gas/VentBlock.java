package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.DuctGeometry;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNetworkComponent;
import g_mungus.zps.block.gas.core.KNodeBlockImpl;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.VentBlockEntity;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
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
 * Vents gas out of the network and into the world through a full-face panel.
 *
 * <p>Ported from Clockwork's {@code ExhaustBlock} (Apache-2.0), minus its Create half — there it
 * doubles as a fan that pushes entities around and drives Create's fan processing. Here it just
 * vents.
 *
 * <p>{@link #FACING} is the side the gas leaves by, and that side alone is closed to the network:
 * the other five join like any duct, so a vent can be teed into the middle of a run rather than
 * only capping the end of one. That is the shape of the redstone converter's plate-and-terminals
 * arrangement, applied to gas.
 *
 * <p>A redstone signal shuts it. The node keeps filling and its pressure keeps climbing — the
 * outlet is simply closed until the signal drops.
 */
public class VentBlock extends KNodeBlockImpl implements EntityBlock, GasNetworkComponent {

    /** The side the panel sits on and gas leaves by. Never joins the network. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Shut while powered. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** Roughly a duct's cross-section over a short stub, in cubic metres. */
    public static final double VOLUME = 0.25;
    /** Vents to open air, so it tolerates far less than a sealed vessel. */
    public static final double MAX_PRESSURE = 16_375_049.0;
    public static final double MAX_TEMPERATURE = 1478.0;

    /**
     * The vented face is a full 16x16 panel, 4 pixels deep whichever way it points. That is deeper
     * than the duct arm it stands in for on every axis, so it always reaches past the core and the
     * two overlap rather than leaving a seam.
     */
    private static final Map<Direction, VoxelShape> PANELS = new EnumMap<>(Direction.class);

    static {
        PANELS.put(Direction.UP, Block.box(0, 12, 0, 16, 16, 16));
        PANELS.put(Direction.DOWN, Block.box(0, 0, 0, 16, 4, 16));
        PANELS.put(Direction.NORTH, Block.box(0, 0, 0, 16, 16, 4));
        PANELS.put(Direction.SOUTH, Block.box(0, 0, 12, 16, 16, 16));
        PANELS.put(Direction.WEST, Block.box(0, 0, 0, 4, 16, 16));
        PANELS.put(Direction.EAST, Block.box(12, 0, 0, 16, 16, 16));
    }

    public VentBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(POWERED, false);
        for (EnumProperty<DuctConnectionType> property : DuctGeometry.connectionProperties()) {
            state = state.setValue(property, DuctConnectionType.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWERED);
        DuctGeometry.connectionProperties().forEach(builder::add);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        // Vents back toward the player, like a dispenser; sneaking turns it away.
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
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

    /** Whether the outlet is currently closed. */
    public static boolean isShut(BlockState state) {
        return state.hasProperty(POWERED) && state.getValue(POWERED);
    }

    // --- gas network ------------------------------------------------------------------------

    /** A short stub of pipe, open at one end. */
    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new PipeDuctNode(pos, NodeBehaviorType.PIPE, new HashSet<>(),
                VOLUME, MAX_PRESSURE, MAX_TEMPERATURE, 1687.5, 449.0);
    }

    /** Joins on every face except the one it vents from. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (state.hasProperty(FACING) && toNeighbor == state.getValue(FACING)) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            boolean connected = direction != state.getValue(FACING)
                    && GasEdgeNegotiator.wouldConnect(level, pos, direction);
            result = result.setValue(DuctGeometry.connectionProperty(direction),
                    connected ? DuctConnectionType.CONNECTION : DuctConnectionType.NONE);
        }
        return result.equals(state) ? null : result;
    }

    // --- shape ------------------------------------------------------------------------------

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape shape = Shapes.joinUnoptimized(PANELS.get(facing),
                DuctGeometry.core(hasHorizontalProfile(state)), BooleanOp.OR);

        for (Direction direction : Direction.values()) {
            if (state.getValue(DuctGeometry.connectionProperty(direction)) != DuctConnectionType.NONE) {
                shape = Shapes.joinUnoptimized(shape, DuctGeometry.arm(direction), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }

    /**
     * A panel on a side face terminates a horizontal run exactly as an arm would, so it widens the
     * core to the 12x8 profile just the same. Only a vent pointing straight up or down can keep the
     * narrow column.
     */
    private static boolean hasHorizontalProfile(BlockState state) {
        return state.getValue(FACING).getAxis() != Direction.Axis.Y
                || DuctGeometry.hasHorizontalConnection(state);
    }

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
        return ModBlockEntities.VENT.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return type != ModBlockEntities.VENT.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((VentBlockEntity) blockEntity).tick();
    }
}
