package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.client.screens.GasGaugeClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
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
 * A dial that reads the pressure or temperature of the gas at the block it is bolted to.
 *
 * <p>Mounted like the {@link VentBlock}: a plate pressed flat against whatever feeds it, joining
 * the network on that one face and nowhere else. Unlike the vent it is sealed — gas that reaches
 * its node stays in the network, it is only measured.
 *
 * <p>{@link #FACING} is the side the dial is read from; the inlet is the face opposite. The block
 * entity holds the measurement settings, drives the needle, and supplies a comparator signal that
 * runs from 0 at the configured lower bound to 15 at the upper.
 */
public class GasGaugeBlock extends GasNodeBlock implements EntityBlock {

    /** The direction the dial faces. The network joins on the opposite face, and nowhere else. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /**
     * Roughly a duct's cross-section over a short stub, in cubic metres — the same as the vent's,
     * so the gauge is no more of a buffer on the line than a vent is.
     */
    public static final double VOLUME = 0.25;
    /** Same ceilings as a plain duct: a gauge is no stronger than the line it reads. */
    public static final double MAX_PRESSURE = 16_375_049.0;
    public static final double MAX_TEMPERATURE = 1478.0;

    /** Thickness of the plate, in pixels. The dial face sits this far in from the inlet face. */
    public static final int PLATE_THICKNESS = 2;
    /** Inset of the plate from the block's edges, in pixels: a 10x10 plate on a 16x16 face. */
    public static final int PLATE_INSET = 3;

    /** A 10x10 plate, {@link #PLATE_THICKNESS} deep, pushed back against the inlet face. */
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

    static {
        int lo = PLATE_INSET;
        int hi = 16 - PLATE_INSET;
        int back = 16 - PLATE_THICKNESS;
        PLATES.put(Direction.UP, Block.box(lo, 0, lo, hi, PLATE_THICKNESS, hi));
        PLATES.put(Direction.DOWN, Block.box(lo, back, lo, hi, 16, hi));
        PLATES.put(Direction.NORTH, Block.box(lo, lo, back, hi, hi, 16));
        PLATES.put(Direction.SOUTH, Block.box(lo, lo, 0, hi, hi, PLATE_THICKNESS));
        PLATES.put(Direction.WEST, Block.box(back, lo, lo, 16, hi, hi));
        PLATES.put(Direction.EAST, Block.box(0, lo, lo, PLATE_THICKNESS, hi, hi));
    }

    public GasGaugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        // The dial faces the player, so it is bolted to the block behind it; sneaking turns it away.
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    // --- gas network ------------------------------------------------------------------------

    /** A short, sealed stub of pipe. Nothing ever leaves it except back down the line. */
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

    // --- interaction ------------------------------------------------------------------------

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        // No inventory, so nothing for a container menu to hold: the settings screen is opened
        // straight from the client, the way the creative gas generator's is.
        if (level.isClientSide()) {
            GasGaugeClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // --- comparator -------------------------------------------------------------------------

    @Override
    protected boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GasGaugeBlockEntity gauge
                ? gauge.getComparatorOutputSignal()
                : 0;
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

    // --- block entity -----------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.GAS_GAUGE.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.GAS_GAUGE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((GasGaugeBlockEntity) blockEntity).tick();
    }
}
