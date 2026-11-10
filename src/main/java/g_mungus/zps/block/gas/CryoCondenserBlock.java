package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.CryoCondenserBlockEntity;
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
 * Freezes gas into items. See {@link CryoCondenserBlockEntity} for how.
 *
 * <p>On the gas network it is a tank with a single port on top, the Vaporizer's outlet turned into
 * an open pipe: gas flows in and out freely, so whatever the machine cannot freeze passes back out
 * rather than getting stuck inside. Keeping the line clear of it is the player's job.
 */
public class CryoCondenserBlock extends GasNodeBlock implements EntityBlock {

    /** Bore and half-length of a connection, matching a plain duct so the geometry lines up. */
    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** The tank's internal volume, in cubic metres. */
    public static final double VOLUME = 4.0;
    /** The same ceiling as a duct. */
    public static final double MAX_PRESSURE = 16_375_049.0;
    /** The same as the Vaporizer's: gas fed in may arrive hot. */
    public static final double MAX_TEMPERATURE = 2500.0;
    /** A heavy vessel, in J/K, as the Vaporizer's is. */
    private static final double HEAT_CAPACITY = 2000.0;

    /** The model's silhouette: a 12-pixel-tall body with the port cap centred on top. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 12, 16),
            Block.box(2, 12, 2, 14, 16, 14));

    /** The port face. */
    public static final Direction PORT = Direction.UP;

    public CryoCondenserBlock(Properties properties) {
        super(properties);
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME,
                MAX_PRESSURE, MAX_TEMPERATURE, HEAT_CAPACITY, 1.0);
    }

    /** Joins the network on the top face alone, both ways. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        if (toNeighbor != PORT) {
            return null;
        }
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
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
        return ModBlockEntities.CRYO_CONDENSER.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CRYO_CONDENSER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CryoCondenserBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof CryoCondenserBlockEntity condenser
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(condenser, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof CryoCondenserBlockEntity condenser) {
            condenser.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
