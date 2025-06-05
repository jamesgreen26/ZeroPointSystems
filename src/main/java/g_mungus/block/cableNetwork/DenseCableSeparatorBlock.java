package g_mungus.block.cableNetwork;

import g_mungus.block.ModBlocks;
import g_mungus.blockentity.TransformerBlockEntity;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DenseCableSeparatorBlock extends Block implements CableNetworkComponent, QuadCableNetworkComponent {

    public static final DirectionProperty FACING = DirectionProperty.create("facing");
    public static final BooleanProperty CONNECTED = BooleanProperty.create("dense_connected");
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");


    public DenseCableSeparatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROTATION, 0)
                .setValue(CONNECTED, false)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
        builder.add(ROTATION);
        builder.add(CONNECTED);
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult arg6) {
        if (player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) {
            level.setBlock(pos, state.setValue(ROTATION, (state.getValue(ROTATION) + 1) % 4), Block.UPDATE_CLIENTS);

            Block block = state.getBlock();

            if (block instanceof DenseCableSeparatorBlock) {
                ((DenseCableSeparatorBlock) block).updateNetwork(pos, level);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return getNewBlockState(
                defaultBlockState()
                        .setValue(FACING, facing)
                        .setValue(CONNECTED, shouldConnectDensePrePlace(context.getLevel(), facing, context.getClickedPos())),
                context.getLevel(),
                context.getClickedPos()
        );
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean currentlyConnected = state.getValue(CONNECTED);
            boolean shouldConnect = shouldConnectDense(level, state.getValue(FACING), pos);

            boolean currentNorth = state.getValue(NORTH);
            boolean currentSouth = state.getValue(SOUTH);
            boolean currentEast = state.getValue(EAST);
            boolean currentWest = state.getValue(WEST);
            boolean currentUp = state.getValue(UP);
            boolean currentDown = state.getValue(DOWN);

            BlockState newState = getNewBlockState(state, level, pos);

            boolean newNorth = newState.getValue(NORTH);
            boolean newSouth = newState.getValue(SOUTH);
            boolean newEast = newState.getValue(EAST);
            boolean newWest = newState.getValue(WEST);
            boolean newUp = newState.getValue(UP);
            boolean newDown = newState.getValue(DOWN);

            boolean connectionsChanged = !(
                    newNorth == currentNorth
                            && newSouth == currentSouth
                            && newEast == currentEast
                            && newWest == currentWest
                            && newUp == currentUp
                            && newDown == currentDown
            );

            if (connectionsChanged || currentlyConnected != shouldConnect) {
                level.setBlock(pos, newState.setValue(CONNECTED, shouldConnect), 3);
            }

            if (currentlyConnected != shouldConnect) {
                updateNetwork(pos, level);
            } else if (connectionsChanged) {
                Channel channel = getChannelForNeighborPos(pos, fromPos, state);

                if (channel != null) {
                    updateNetworkForChannel(pos, level, channel, state);
                }
            }
        }
    }

    private boolean shouldConnectDensePrePlace(Level level, Direction facing, BlockPos pos) {
        BlockPos neighborPos = pos.offset(facing.getNormal());
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        return (neighborBlock instanceof QuadCableNetworkAble
                && ((QuadCableNetworkAble) neighborBlock).canQuadConnectTo(neighborPos, pos, neighborState)
        );
    }

    private boolean shouldConnectDense(Level level, Direction facing, BlockPos pos) {
        BlockPos neighborPos = pos.offset(facing.getNormal());
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        return (neighborBlock instanceof QuadCableNetworkAble
                && ((QuadCableNetworkAble) neighborBlock).canQuadConnectTo(neighborPos, pos, neighborState)
                && this.canQuadConnectTo(pos, neighborPos, level.getBlockState(pos))
        );
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
    public Map<BlockPos, BlockPos> getConnectedPositions(Level level, BlockPos selfPos, BlockPos from) {
        Map<BlockPos, BlockPos> result = new HashMap<>();
        Channel channel = getChannelForNeighborPos(selfPos, from, level.getBlockState(selfPos));
        BlockPos end = getOtherEnd(level, selfPos);

        if (channel == null || end == null) return result;

        BlockState endState = level.getBlockState(end);

        if (endState.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) {
            DenseCableSeparatorBlock endBlock = (DenseCableSeparatorBlock) endState.getBlock();
            BlockPos neighborPos = endBlock.getNeighborPosForChannel(channel, end, endState);
            BlockState neighborState = level.getBlockState(neighborPos);
            Direction connectionDir = Direction.fromDelta(end.getX() - neighborPos.getX(), end.getY() - neighborPos.getY(), end.getZ() - neighborPos.getZ());

            if (neighborState.getBlock() instanceof CanConnectCables && ((CanConnectCables) neighborState.getBlock()).shouldCablesConnectToThis(neighborState, connectionDir)) {
                result.put(neighborPos, end);
            }
        }

        return result;
    }

    @Override
    public void updateNetwork(BlockPos pos, Level level) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) return;
        Arrays.stream(Channel.values()).forEach(channel -> {
            updateNetworkForChannel(pos, level, channel, state);
        });
    }

    private static void updateNetworkForChannel(BlockPos pos, Level level, Channel channel, BlockState state) {
        BlockPos neighborPos = ((DenseCableSeparatorBlock) state.getBlock()).getNeighborPosForChannel(channel, pos, state);
        Direction connectionDir = Direction.fromDelta(pos.getX() - neighborPos.getX(), pos.getY() - neighborPos.getY(), pos.getZ() - neighborPos.getZ());
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        Queue<Pair<BlockPos, BlockPos>> toCheck = new ArrayDeque<>();
        List<BlockPos> checked = new ArrayList<>();
        Map<BlockPos, TransformerBlock.TransformerType> transformers = new HashMap<>();

        checked.add(pos);

        if (neighborBlock instanceof CanConnectCables && ((CanConnectCables) neighborBlock).shouldCablesConnectToThis(neighborState, connectionDir)) {
            toCheck.add(new Pair<>(neighborPos, pos));
        }

        Map<BlockPos, BlockPos> otherEnd = ((DenseCableSeparatorBlock) state.getBlock()).getConnectedPositions(level, pos, neighborPos);
        otherEnd.forEach((key, value) -> {
            toCheck.add(new Pair<>(key, value));
        });

        while (!toCheck.isEmpty()) {
            Pair<BlockPos, BlockPos> current = toCheck.poll();
            if (checked.contains(current.getFirst())) continue;
            checked.add(current.getFirst());

            Block block = level.getBlockState(current.getFirst()).getBlock();
            if (block instanceof TransformerBlock) {
                TransformerBlock.TransformerType type = ((TransformerBlock) block).getTransformerType();
                transformers.put(current.getFirst(), type);
            }

            if (block instanceof CableNetworkComponent) {
                ((CableNetworkComponent) block).getConnectedPositions(level, current.component1(), current.component2()).forEach((key, value) -> {
                    toCheck.add(new Pair<>(key, value));
                });
            }
        }

        transformers.keySet().forEach(blockPos -> {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);

            if (blockEntity instanceof TransformerBlockEntity) {
                ((TransformerBlockEntity) blockEntity).updateTransformers(transformers);
            }
        });
    }

    @Override
    public boolean shouldCablesConnectToThis(BlockState blockState, Direction direction) {
        return blockState.getValue(FACING).getAxis() != direction.getAxis();
    }

    public @Nullable BlockPos getOtherEnd(Level level, BlockPos self) {
        BlockState state = level.getBlockState(self);
        if (state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) {
            Direction facing = state.getValue(FACING);
            BlockPos next = self.offset(facing.getNormal());

            BlockState nextState = level.getBlockState(next);
            Block nextBlock = nextState.getBlock();

            if (nextBlock instanceof QuadCableNetworkAble && ((QuadCableNetworkAble) nextBlock).canQuadConnectTo(next, self, nextState)) {
                if (nextBlock instanceof QuadCableNetworkComponent) {
                    return next;
                } else if (nextBlock instanceof QuadCableNetworkConnector) {
                    return ((QuadCableNetworkConnector) nextBlock).getConnectedComponent(next, self, nextState, level, 0);
                }
            }
        }
        return null;
    }

    public BlockPos getNeighborPosForChannel(Channel channel, BlockPos separator, BlockState state) {
        if (!state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) return separator;
        Direction facing = state.getValue(FACING);
        int rotation = state.getValue(ROTATION);

        Vec3i c0;
        if (!facing.getAxis().equals(Direction.Axis.Y)) {
            c0 = Direction.UP.getNormal();
        } else if (facing.equals(Direction.UP)) {
            c0 = Direction.SOUTH.getNormal();
        } else {
            c0 = Direction.NORTH.getNormal();
        }

        int index = (8 + channel.getIndex() - rotation) % 4;

        Vec3i selected = switch (index) {
            default -> c0;
            case 1 -> facing.getNormal().cross(c0).multiply(-1);
            case 2 -> c0.multiply(-1);
            case 3 -> facing.getNormal().cross(c0);
        };

        return separator.offset(selected);
    }

    public @Nullable QuadCableNetworkComponent.Channel getChannelForNeighborPos(BlockPos separator, BlockPos neighbor, BlockState state) {
        if (!state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) return null;
        Direction facing = state.getValue(FACING);
        int rotation = state.getValue(ROTATION);

        Vec3i c0;
        if (!facing.getAxis().equals(Direction.Axis.Y)) {
            c0 = Direction.UP.getNormal();
        } else if (facing.equals(Direction.UP)) {
            c0 = Direction.SOUTH.getNormal();
        } else {
            c0 = Direction.NORTH.getNormal();
        }

        Vec3i offset = neighbor.subtract(separator);

        // Check if the offset matches any of the possible channel positions
        if (offset.equals(c0)) {
            return Channel.values()[(rotation) % 4];
        } else if (offset.equals(facing.getNormal().cross(c0).multiply(-1))) {
            return Channel.values()[(rotation + 1) % 4];
        } else if (offset.equals(c0.multiply(-1))) {
            return Channel.values()[(rotation + 2) % 4];
        } else if (offset.equals(facing.getNormal().cross(c0))) {
            return Channel.values()[(rotation + 3) % 4];
        }

        return null;
    }

    @Override
    public boolean canQuadConnectTo(BlockPos self, BlockPos to, BlockState selfState) {
        if (!selfState.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) return false;
        return self.offset(selfState.getValue(FACING).getNormal()).equals(to);
    }
}
