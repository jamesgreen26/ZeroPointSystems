package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNetworkComponent;
import g_mungus.zps.block.gas.core.KNodeBlockImpl;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.VaporizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode;

import java.util.HashSet;

/**
 * Vaporizes blue ice and lithium into Flux, holding it in an internal pressurised buffer.
 *
 * <p>It is a tank node rather than a pipe: a real volume that fills with gas. It offers a
 * connection on one face only — the outlet it points at — so Flux has a single, deliberate way out.
 */
public class VaporizerBlock extends KNodeBlockImpl implements EntityBlock, GasNetworkComponent {

    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** The internal buffer, in cubic metres. */
    public static final double VOLUME = 2.0;
    /**
     * The buffer's pressure ceiling, and so how much Flux it holds before production stalls.
     *
     * <p>Sized to the machine rather than inherited from Kelvin's pipe default of ~16 MPa: one
     * batch of the shipped recipe raises this node by roughly 0.9 MPa, so that default would take
     * about sixteen batches to fill and move the gauge two pixels at a time. At 4 MPa the buffer
     * holds a little under four batches and the gauge actually reads.
     */
    public static final double MAX_PRESSURE = 4_000_000.0;
    public static final double MAX_TEMPERATURE = 1478.0;
    private static final double HEAT_CONDUCTIVITY = 1687.5;
    private static final double HEAT_CAPACITY = 449.0;

    public VaporizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DirectionalBlock.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DirectionalBlock.FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        Direction outlet = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
        return defaultBlockState().setValue(DirectionalBlock.FACING, outlet);
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(DirectionalBlock.FACING, rotation.rotate(state.getValue(DirectionalBlock.FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(DirectionalBlock.FACING)));
    }

    // --- gas network ------------------------------------------------------------------------

    /** A tank, not a pipe: the buffer is a real volume that fills with gas. */
    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME, MAX_PRESSURE,
                MAX_TEMPERATURE, HEAT_CONDUCTIVITY, HEAT_CAPACITY, 1.0);
    }

    /** Offers a connection on its outlet face only. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(DirectionalBlock.FACING)
                || toNeighbor != state.getValue(DirectionalBlock.FACING)) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    // --- block entity -----------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.VAPORIZER.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return type != ModBlockEntities.VAPORIZER.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) -> ((VaporizerBlockEntity) blockEntity).tick();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!newState.is(state.getBlock()) && level.getBlockEntity(pos) instanceof VaporizerBlockEntity vaporizer) {
            vaporizer.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
