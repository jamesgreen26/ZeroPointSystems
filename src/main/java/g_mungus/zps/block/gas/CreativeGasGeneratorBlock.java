package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNetworkComponent;
import g_mungus.zps.block.gas.core.KNodeBlockImpl;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.client.screens.CreativeGasGeneratorClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A creative source of gas: emits a chosen gas, at a chosen rate, at a chosen temperature, out of
 * every face.
 *
 * <p>It is a tank node like the vaporizer, but unlike the vaporizer it offers a connection on all
 * six faces, so a duct run can be hung off it in any direction without thinking about orientation.
 *
 * <p>Its node is given no pressure or temperature ceiling at all. Kelvin ruptures a node whose
 * pressure passes {@code maxPressure} and destroys one whose walls pass {@code maxTemperature}, so
 * a bounded creative source left running into a sealed line would blow itself up within seconds.
 * The ducts hanging off it keep their normal limits — bursting a line you over-pressurised is
 * exactly the feedback this block exists to give.
 */
public class CreativeGasGeneratorBlock extends KNodeBlockImpl implements EntityBlock, GasNetworkComponent {

    /** Bore and half-length of a connection, matching a plain duct so the geometry lines up. */
    private static final double RADIUS = 0.125;
    private static final double HALF_LENGTH = 0.25;

    /** The internal volume, in cubic metres — a duct's, so pressure reads on the same scale. */
    public static final double VOLUME = 1.0;
    private static final double HEAT_CONDUCTIVITY = 1687.5;
    private static final double HEAT_CAPACITY = 449.0;

    public CreativeGasGeneratorBlock(Properties properties) {
        super(properties);
    }

    // --- gas network ------------------------------------------------------------------------

    /** A tank with no ceiling: see the class javadoc. */
    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new TankDuctNode(pos, NodeBehaviorType.TANK, new HashSet<>(), VOLUME,
                Double.MAX_VALUE, Double.MAX_VALUE, HEAT_CONDUCTIVITY, HEAT_CAPACITY, 1.0);
    }

    /** Offers a plain connection on every face, and imposes nothing on it. */
    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        return GasEdgeProposal.pipe(RADIUS, HALF_LENGTH);
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return null;
    }

    // --- block entity -----------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.CREATIVE_GAS_GENERATOR.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CREATIVE_GAS_GENERATOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CreativeGasGeneratorBlockEntity) blockEntity).tick();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        // No inventory, so nothing for a container menu to hold: the settings screen is opened
        // straight from the client instead.
        if (level.isClientSide()) {
            CreativeGasGeneratorClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
