package g_mungus.zps.block.reactor;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.reactor.ReactorWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.HashSet;

/**
 * A reactor wall block that carries gas across the wall: the Fuel Injector and the Exhaust Port.
 *
 * <p>Each holds a short stub of pipe as its own Kelvin node and joins the network on its outer
 * face only, through a check valve pointing whichever way the block passes gas. The chamber side
 * is not an ordinary edge — the reactor manages that once the cavity seals — so the stub is a
 * dead end until the block is part of a reactor.
 *
 * <p>{@link #FACING} is the outer face, chosen at placement the way a vent is: toward the player,
 * or away while sneaking. A block whose inner face is not on the cavity still seals the shell but
 * does nothing.
 */
public abstract class ReactorGasWallBlock extends GasNodeBlock implements EntityBlock, ReactorWallBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;
    /** Roughly a duct's cross-section over a short stub, in cubic metres. */
    public static final double VOLUME = 0.25;
    private static final double HEAT_CAPACITY = 449.0;

    protected ReactorGasWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Which way gas may cross the outer face, as a direction relative to this block. */
    protected abstract Direction allowedFlow(Direction facing);

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    // --- gas network ------------------------------------------------------------------------

    /**
     * No ceiling of its own: the stub is part of the reactor wall, and the reactor decides when
     * the wall fails. An injector never receives chamber gas and an exhaust port cools what it
     * takes, so neither gets anywhere near a duct's limits in normal use.
     */
    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new PipeDuctNode(pos, NodeBehaviorType.PIPE, new HashSet<>(),
                VOLUME, Double.MAX_VALUE, Double.MAX_VALUE, HEAT_CAPACITY);
    }

    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(FACING) || toNeighbor != state.getValue(FACING)) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH)
                .with(new OneWayFacet(allowedFlow(state.getValue(FACING))));
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    // --- reactor membership -----------------------------------------------------------------

    @Override
    protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                           @NotNull BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            ReactorWallBlock.onPlaced(level, pos);
        }
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!newState.is(this)) {
            // Before the node goes, so the reactor can take its internal edge down cleanly.
            ReactorWallBlock.onRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
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
}
