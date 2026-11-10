package g_mungus.zps.block.gas;

import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.block.gas.core.facets.GasFilterFacet;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import g_mungus.zps.client.screens.GasFilterClientHooks;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
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

import java.util.HashSet;

/**
 * A length of duct that holds certain gases back. It joins the network on every face like a
 * duct, at a duct's bore, and carries gas either way; the gases picked in its screen never cross
 * any of its faces, so they can neither get in nor get out.
 *
 * <p>The filter rides on the negotiated edges as a {@link GasFilterFacet}, so a filter against a
 * pump or a check valve keeps both behaviours on the one connection. Right-click opens the screen.
 */
public class GasFilterBlock extends GasNodeBlock implements EntityBlock {

    /** The filter housing: a 12x12 box, with the duct's arms on connected faces. */
    private static final VoxelShape HOUSING = Block.box(2, 2, 2, 14, 14, 14);

    public GasFilterBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(DuctBlock.propertyFor(direction), DuctConnectionType.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        for (Direction direction : Direction.values()) {
            builder.add(DuctBlock.propertyFor(direction));
        }
    }

    // --- gas network ------------------------------------------------------------------------

    @Override
    public @NotNull DuctNode createNode(@NotNull DuctNodePos pos) {
        return new PipeDuctNode(pos, NodeBehaviorType.PIPE, new HashSet<>(), DuctBlock.VOLUME,
                DuctBlock.MAX_PRESSURE, DuctBlock.MAX_TEMPERATURE, DuctBlock.HEAT_CAPACITY);
    }

    @Override
    public @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor) {
        GasEdgeProposal pipe = GasEdgeProposal.pipe(DuctBlock.RADIUS, DuctBlock.HALF_LENGTH);
        GasFilter filter = level.getBlockEntity(self) instanceof GasFilterBlockEntity blockEntity
                ? blockEntity.getFilter()
                : GasFilter.PASS_ALL;
        return filter.blocked().isEmpty() ? pipe : pipe.with(new GasFilterFacet(filter));
    }

    @Override
    public @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            DuctConnectionType connection = GasEdgeNegotiator.wouldConnect(level, pos, direction)
                    ? DuctConnectionType.CONNECTION
                    : DuctConnectionType.NONE;
            result = result.setValue(DuctBlock.propertyFor(direction), connection);
        }
        return result.equals(state) ? null : result;
    }

    // --- interaction ------------------------------------------------------------------------

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull BlockHitResult hit) {
        // No inventory, so no container menu: the screen opens straight from the client.
        if (level.isClientSide()) {
            GasFilterClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // --- shape ------------------------------------------------------------------------------

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape shape = HOUSING;
        for (Direction direction : Direction.values()) {
            if (state.getValue(DuctBlock.propertyFor(direction)) != DuctConnectionType.NONE) {
                shape = Shapes.joinUnoptimized(shape, DuctBlock.armShape(direction), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }

    /** Faces travel with the block, as a duct's do. */
    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        BlockState result = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(DuctBlock.propertyFor(rotation.rotate(direction)),
                    state.getValue(DuctBlock.propertyFor(direction)));
        }
        return result;
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        BlockState result = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(DuctBlock.propertyFor(mirror.mirror(direction)),
                    state.getValue(DuctBlock.propertyFor(direction)));
        }
        return result;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.GAS_FILTER.get().create(pos, state);
    }
}
