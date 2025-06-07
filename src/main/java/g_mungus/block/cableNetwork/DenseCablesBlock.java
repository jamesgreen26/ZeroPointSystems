package g_mungus.block.cableNetwork;

import g_mungus.block.ModBlocks;
import g_mungus.block.cableNetwork.core.CableNetworkComponent;
import g_mungus.block.cableNetwork.core.Channels;
import g_mungus.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DenseCablesBlock extends Block implements CableNetworkComponent {
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
        return this.defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updateConnections(state, level, pos);
        }
    }

    private void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState)) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        }
    }

    private BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        return state.setValue(CONNECTIONS, getConnectedNodes(new NetworkNode(pos, Channels.QUAD_1, false), level, List.of()).size());
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public int getTotalChannelCount() {
        return 4;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        if (isConnectingSide(self, from, state)) {
            return 4;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        Direction.Axis axis = state.getValue(AXIS);
        return List.of(
                self.pos().offset(Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal()),
                self.pos().offset(Direction.get(Direction.AxisDirection.NEGATIVE, axis).getNormal())
        );
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.toQuad(input.channel());
    }

    private boolean isConnectingSide(BlockPos a, BlockPos b, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        Vec3i delta = a.subtract(b);
        Direction connection = Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        if (connection != null) {
            return connection.getAxis() == axis;
        } else {
            return false;
        }
    }
}