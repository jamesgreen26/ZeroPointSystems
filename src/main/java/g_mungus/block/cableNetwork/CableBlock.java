package g_mungus.block.cableNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CableBlock extends Block implements CableNetworkComponent {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 0, 10, 10, 6);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 10, 10, 10, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(10, 6, 6, 16, 10, 10);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 6, 6, 6, 10, 10);
    private static final VoxelShape UP_SHAPE = Block.box(6, 10, 6, 10, 16, 10);
    private static final VoxelShape DOWN_SHAPE = Block.box(6, 0, 6, 10, 6, 10);

    public CableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;

        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_SHAPE);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_SHAPE);

        return shape;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext arg) {
        BlockState blockState = this.defaultBlockState();
        return getNewBlockState(blockState, arg.getLevel(), arg.getClickedPos());
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

    @NotNull
    BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean north = canFormConnection(state, level, pos, Direction.NORTH);
        boolean south = canFormConnection(state, level, pos, Direction.SOUTH);
        boolean east = canFormConnection(state, level, pos, Direction.EAST);
        boolean west = canFormConnection(state, level, pos, Direction.WEST);
        boolean up = canFormConnection(state, level, pos, Direction.UP);
        boolean down = canFormConnection(state, level, pos, Direction.DOWN);

        return state
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    private boolean canFormConnection(BlockState state, Level level, BlockPos pos, Direction direction) {
        boolean shouldConnectToThis = shouldCablesConnectToThis(state, direction);
        boolean shouldConnectToOther = otherCanConnect(level, pos.offset(direction.getNormal()), direction.getOpposite());
        return shouldConnectToThis && shouldConnectToOther;
    }

    private boolean otherCanConnect(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return (block instanceof CanConnectCables && ((CanConnectCables) block).shouldCablesConnectToThis(state, direction));
    }

    @Override
    public boolean shouldCablesConnectToThis(BlockState blockState, Direction direction) {
        return true;
    }

    @Override
    public List<ConnectionAdjacency> getConnectedPositions(Level level, BlockPos selfPos, BlockPos from) {
        List<ConnectionAdjacency> connections = new ArrayList<>();
        BlockState state = level.getBlockState(selfPos);

        if (state.getBlock() instanceof CableBlock) {
            if (state.getValue(UP)) {
                connections.add(new ConnectionAdjacency(selfPos.above(), selfPos, -1));
            }
            if (state.getValue(DOWN)) {
                connections.add(new ConnectionAdjacency(selfPos.below(), selfPos, -1));
            }
            if (state.getValue(NORTH)) {
                connections.add(new ConnectionAdjacency(selfPos.north(), selfPos, -1));
            }
            if (state.getValue(SOUTH)) {
                connections.add(new ConnectionAdjacency(selfPos.south(), selfPos, -1));
            }
            if (state.getValue(EAST)) {
                connections.add(new ConnectionAdjacency(selfPos.east(), selfPos, -1));
            }
            if (state.getValue(WEST)) {
                connections.add(new ConnectionAdjacency(selfPos.west(), selfPos, -1));
            }
        }

        connections.forEach(connectionAdjacency -> {
            if (!(level.getBlockState(connectionAdjacency.getFirst()).getBlock() instanceof CableNetworkComponent)) {
                connections.remove(connectionAdjacency);
            }
        });

        return connections;
    }
}