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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
 * <p>On the gas network it is a tank that offers a connection on every face, so ducts can be run
 * off it in any direction — but every connection is a check valve pointing outward, so gas leaves
 * the machine and never comes back in. The tank keeps a duct's pressure ceiling — the block entity stops
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

    public VaporizerBlock(Properties properties) {
        super(properties);
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME,
                MAX_PRESSURE, MAX_TEMPERATURE, HEAT_CAPACITY, 1.0);
    }

    /** Offers a connection on every face that only ever carries gas away from the machine. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH)
                .with(new OneWayFacet(toNeighbor));
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
