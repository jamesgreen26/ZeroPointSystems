package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
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
import net.minecraft.world.phys.shapes.CollisionContext;
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
 * <p>The block is a plate bolted flat against whatever feeds it: {@link #FACING} is the side the gas
 * leaves by, and the single face opposite it is the inlet. Nothing else on the block joins the
 * network, so a vent always caps the end of a run rather than teeing into the middle of one.
 *
 * <p>A redstone signal shuts it. The node keeps filling and its pressure keeps climbing — the
 * outlet is simply closed until the signal drops.
 */
public class VentBlock extends GasNodeBlock implements EntityBlock {

    /** The direction the gas leaves by. The network joins on the opposite face, and nowhere else. */
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
     * Thickness of the plate, in pixels. The jet leaves from its outer face, so
     * {@link g_mungus.zps.blockentity.gas.VentBlockEntity} measures its mouth from this.
     */
    public static final int PLATE_THICKNESS = 3;

    /**
     * The whole block: a 16x16 plate pushed back against its inlet face, so it sits flush on the
     * duct or machine feeding it and vents into the open in front.
     */
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

    static {
        int back = 16 - PLATE_THICKNESS;
        PLATES.put(Direction.UP, Block.box(0, 0, 0, 16, PLATE_THICKNESS, 16));
        PLATES.put(Direction.DOWN, Block.box(0, back, 0, 16, 16, 16));
        PLATES.put(Direction.NORTH, Block.box(0, 0, back, 16, 16, 16));
        PLATES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, PLATE_THICKNESS));
        PLATES.put(Direction.WEST, Block.box(back, 0, 0, 16, 16, 16));
        PLATES.put(Direction.EAST, Block.box(0, 0, 0, PLATE_THICKNESS, 16, 16));
    }

    public VentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWERED);
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
                VOLUME, MAX_PRESSURE, MAX_TEMPERATURE, 449.0);
    }

    /** Joins on the inlet face alone: the one the plate is pressed against. */
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
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return PLATES.get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
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
