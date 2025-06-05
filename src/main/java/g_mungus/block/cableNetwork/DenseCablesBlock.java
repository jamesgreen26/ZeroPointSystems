package g_mungus.block.cableNetwork;

import g_mungus.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public class DenseCablesBlock extends Block implements QuadCableNetworkConnector {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 2);


    public DenseCablesBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(AXIS, Direction.Axis.X)
                        .setValue(CONNECTIONS, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, CONNECTIONS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getNearestLookingDirection().getAxis();
        return this.defaultBlockState().setValue(AXIS, axis).setValue(CONNECTIONS, getConnections(axis, context.getClickedPos(), context.getLevel()));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            int currentConnections = state.getValue(CONNECTIONS);
            int newConnections = getConnections(state.getValue(AXIS), pos, level);

            if (currentConnections != newConnections) {
                level.setBlock(pos, state.setValue(CONNECTIONS, newConnections), 3);

                Direction.Axis axis = state.getValue(AXIS);

                Direction optionA = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                Direction optionB = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);

                BlockPos neighborA = pos.offset(optionA.getNormal());
                BlockPos neighborB = pos.offset(optionB.getNormal());

                @Nullable ConnectionAdjacency componentA = getConnectedComponent(pos, neighborA, state, level, 0);

                if (componentA != null) {
                    BlockState componentState = level.getBlockState(componentA.getFirst());
                    Block component = componentState.getBlock();

                    if (component instanceof CableNetworkComponent) {
                        ((CableNetworkComponent) component).updateNetwork(componentA.getFirst(), level);
                        return;
                    }
                }

                @Nullable ConnectionAdjacency componentB = getConnectedComponent(pos, neighborB, state, level, 0);

                if (componentB != null) {
                    BlockState componentState = level.getBlockState(componentB.getFirst());
                    Block component = componentState.getBlock();

                    if (component instanceof CableNetworkComponent) {
                        ((CableNetworkComponent) component).updateNetwork(componentB.getFirst(), level);
                    }
                }
            }
        }
    }

    @Override
    public @Nullable ConnectionAdjacency getConnectedComponent(BlockPos self, BlockPos from, BlockState selfState, Level level, int recursion) {
        if (recursion > 256) return null;

        if (selfState.is(ModBlocks.DENSE_CABLES.get())) {
            Direction optionA = Direction.fromAxisAndDirection(selfState.getValue(AXIS), Direction.AxisDirection.POSITIVE);
            Direction optionB = Direction.fromAxisAndDirection(selfState.getValue(AXIS), Direction.AxisDirection.NEGATIVE);

            BlockPos to;

            if (from.equals(self.offset(optionA.getNormal()))) {
                to = self.offset(optionB.getNormal());
            } else {
                to = self.offset(optionA.getNormal());
            }

            BlockState toState = level.getBlockState(to);
            Block toBlock = toState.getBlock();

            if (toBlock instanceof QuadCableNetworkAble && ((QuadCableNetworkAble) toBlock).canQuadConnectTo(to, self, toState)) {
                if (toBlock instanceof QuadCableNetworkConnector) {
                    return ((QuadCableNetworkConnector) toBlock).getConnectedComponent(to, self, toState, level, recursion + 1);
                } else if (toBlock instanceof QuadCableNetworkComponent) {
                    return new ConnectionAdjacency(to, self, -1);
                }
            }
        }

        return null;
    }

    int getConnections(Direction.Axis axis, BlockPos pos, Level level) {
        int result = 0;

        Direction optionA = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction optionB = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);

        BlockPos neighborA = pos.offset(optionA.getNormal());
        BlockPos neighborB = pos.offset(optionB.getNormal());

        BlockState stateA = level.getBlockState(neighborA);
        BlockState stateB = level.getBlockState(neighborB);

        Block blockA = stateA.getBlock();
        Block blockB = stateB.getBlock();
        if (blockA instanceof QuadCableNetworkAble && ((QuadCableNetworkAble) blockA).canQuadConnectTo(neighborA, pos, stateA)) {
            result++;
        }
        if (blockB instanceof QuadCableNetworkAble && ((QuadCableNetworkAble) blockB).canQuadConnectTo(neighborB, pos, stateB)) {
            result++;
        }
        return result;
    }

    @Override
    public boolean canQuadConnectTo(BlockPos self, BlockPos to, BlockState selfState) {

        if (selfState.is(ModBlocks.DENSE_CABLES.get())) {
            Direction optionA = Direction.fromAxisAndDirection(selfState.getValue(AXIS), Direction.AxisDirection.POSITIVE);
            Direction optionB = Direction.fromAxisAndDirection(selfState.getValue(AXIS), Direction.AxisDirection.NEGATIVE);

            return to.equals(self.offset(optionA.getNormal())) || to.equals(self.offset(optionB.getNormal()));
        }
        return false;
    }
}