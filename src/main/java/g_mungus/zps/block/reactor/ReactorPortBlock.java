package g_mungus.zps.block.reactor;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.client.screens.ReactorPortClientHooks;
import g_mungus.zps.reactor.ReactorWallBlock;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.HashSet;

/**
 * A reactor wall block that carries gas across the wall, one way, in whichever direction its
 * {@link #MODE} says: an input lets gas in and never out, an output pumps gas out and never lets
 * any in. Right-click opens a screen to switch between the two and to set which gases pass.
 *
 * <p>The block holds a short stub of pipe as its own Kelvin node and joins the network on its
 * outer face only, as an ordinary length of duct. Everything that makes the port a port happens
 * on the chamber side, between the stub and the reactor's chamber node: the check valve, the
 * redstone throttle, the gas filter, and in output mode the pump that draws gas out. That side is not a
 * negotiated edge — the block entity manages it once the cavity seals — so the stub is a dead end
 * until the block is part of a reactor.
 *
 * <p>Redstone throttles the chamber side: the stronger the signal reaching the block, the narrower
 * the passage, until a full fifteen shuts it completely. Unpowered, the port carries its full rate.
 *
 * <p>{@link #FACING} is the outer face, chosen at placement the way a vent is: toward the player,
 * or away while sneaking. A block whose inner face is not on the cavity still seals the shell but
 * does nothing.
 */
public class ReactorPortBlock extends GasNodeBlock implements EntityBlock, ReactorWallBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<ReactorPortMode> MODE = EnumProperty.create("mode", ReactorPortMode.class);

    public static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;
    /** Roughly a duct's cross-section over a short stub, in cubic metres. */
    public static final double VOLUME = 0.25;
    private static final double HEAT_CAPACITY = 449.0;

    public ReactorPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODE, ReactorPortMode.INPUT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, MODE);
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

    public static ReactorPortMode mode(BlockState state) {
        return state.getValue(MODE);
    }

    // --- gas network ------------------------------------------------------------------------

    /**
     * No ceiling of its own: the stub is part of the reactor wall, and the reactor decides when
     * the wall fails. An input never receives chamber gas and an output cools what it takes, so
     * neither mode gets anywhere near a duct's limits in normal use.
     */
    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new PipeDuctNode(pos, NodeBehaviorType.PIPE, new HashSet<>(),
                VOLUME, Double.MAX_VALUE, Double.MAX_VALUE, HEAT_CAPACITY);
    }

    /** The outer face is a plain length of duct; the mode and the throttle act on the chamber side. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(FACING) || toNeighbor != state.getValue(FACING)) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
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

    // --- interaction ------------------------------------------------------------------------

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        // No inventory, so nothing for a container menu to hold: the mode screen is opened
        // straight from the client, the way the gas gauge's is.
        if (level.isClientSide()) {
            ReactorPortClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // --- redstone ---------------------------------------------------------------------------

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                   @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) {
            port.refreshRedstoneLevel();
        }
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

    // --- block entity -----------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.REACTOR_PORT.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlockEntities.REACTOR_PORT.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((ReactorPortBlockEntity) blockEntity).serverTick();
    }
}
