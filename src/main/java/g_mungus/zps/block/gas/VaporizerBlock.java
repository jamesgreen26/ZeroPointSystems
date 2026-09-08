package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.VaporizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode;

import java.util.HashSet;

/**
 * Vaporizes items into gas. See {@link VaporizerBlockEntity} for how.
 *
 * <p>On the gas network it is a tank with a single outlet on top: a check valve pointing upward,
 * so gas leaves the machine there and never comes back in, and nothing joins on any other face.
 * The tank keeps a duct's pressure ceiling — the block entity stops
 * vaporizing before it gets there — and a generous temperature one, since it is meant to hold gas
 * that was made hot on purpose.
 */
public class VaporizerBlock extends GasNodeBlock implements EntityBlock {

    /** Bore and half-length of a connection, matching a plain duct so the geometry lines up. */
    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** The tank's internal volume, in cubic metres. */
    public static final double VOLUME = 4.0;
    /** The same ceiling as a duct: filling past what the line can take gains nothing. */
    public static final double MAX_PRESSURE = 16_375_049.0;
    /** Well above any sensible vaporizing temperature. */
    public static final double MAX_TEMPERATURE = 2500.0;
    /** A heavy vessel, in J/K: hot gas landing in it does not swing its temperature much. */
    private static final double HEAT_CAPACITY = 2000.0;

    /** The model's silhouette: a 12-pixel-tall body with the outlet cap centred on top. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 12, 16),
            Block.box(2, 12, 2, 14, 16, 14));

    public VaporizerBlock(Properties properties) {
        super(properties);
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME,
                MAX_PRESSURE, MAX_TEMPERATURE, HEAT_CAPACITY, 1.0);
    }

    /** The outlet face. */
    public static final Direction OUTLET = Direction.UP;

    /** Joins the network on the top face alone, and only ever carries gas away from the machine. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        if (toNeighbor != OUTLET) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH)
                .with(new OneWayFacet(toNeighbor));
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    // --- shape ------------------------------------------------------------------------------

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
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
        if (level.isClientSide() || type != ModBlockEntities.VAPORIZER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((VaporizerBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof VaporizerBlockEntity vaporizer
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(vaporizer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof VaporizerBlockEntity vaporizer) {
            vaporizer.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
