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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DenseCableBend extends Block implements CableNetworkComponent {
    public static final DirectionProperty DIRECTION_A = DirectionProperty.create("facing_a");
    public static final DirectionProperty DIRECTION_B = DirectionProperty.create("facing_b");
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 2);

    public DenseCableBend(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(DIRECTION_A, Direction.UP)
                        .setValue(DIRECTION_B, Direction.NORTH)
                        .setValue(CONNECTIONS, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION_A, DIRECTION_B, CONNECTIONS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() == null) {
            return this.defaultBlockState()
                    .setValue(DIRECTION_A, Direction.UP)
                    .setValue(DIRECTION_B, Direction.NORTH)
                    .setValue(CONNECTIONS, 0);
        }

        float pitch = context.getPlayer().getXRot();
        float yaw = context.getPlayer().getYRot();

        if (context.getPlayer().isShiftKeyDown()) {
            yaw = yaw + 180;
        }

        // Normalize angles to 0-360
        yaw = (yaw + 360) % 360;
        pitch = (pitch + 360) % 360;

        // Get both directions from the angles
        Direction[] directions = getDirectionsFromPitchYaw(pitch, yaw);

        return this.defaultBlockState()
                .setValue(DIRECTION_A, directions[0])
                .setValue(DIRECTION_B, directions[1]);
    }

    private Direction[] getDirectionsFromPitchYaw(float pitch, float yaw) {
        // Convert pitch to 0-360 range where 0 is looking straight ahead
        float normalizedPitch = (pitch + 90) % 360;
        
        // Determine vertical direction
        Direction verticalDir;
        if (normalizedPitch < 45 || normalizedPitch >= 315) {
            verticalDir = Direction.UP;
        } else if (normalizedPitch >= 135 && normalizedPitch < 225) {
            verticalDir = Direction.DOWN;
        } else {
            verticalDir = null; // Looking horizontally
        }

        // If looking mostly up or down, use that as primary direction
        if (verticalDir != null) {
            Direction horizontalDir;
            if (yaw >= 315 || yaw < 45) {
                horizontalDir = Direction.SOUTH;
            } else if (yaw >= 45 && yaw < 135) {
                horizontalDir = Direction.WEST;
            } else if (yaw >= 135 && yaw < 225) {
                horizontalDir = Direction.NORTH;
            } else {
                horizontalDir = Direction.EAST;
            }

            return new Direction[]{verticalDir.getOpposite(), horizontalDir.getOpposite()};
        } else {
            Direction primary;
            Direction secondary;
            if (yaw >= 0 && yaw < 90) {
                primary = Direction.NORTH;
                secondary = Direction.EAST;
            } else if (yaw >= 90 && yaw < 180) {
                primary = Direction.EAST;
                secondary = Direction.SOUTH;
            } else if (yaw >= 180 && yaw < 270) {
                primary = Direction.SOUTH;
                secondary = Direction.WEST;
            } else {
                primary = Direction.WEST;
                secondary = Direction.NORTH;
            }
            return new Direction[]{primary, secondary};
        }
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
        Direction first = state.getValue(DIRECTION_A);
        Direction second = state.getValue(DIRECTION_B);
        return List.of(
                self.pos().offset(first.getNormal()),
                self.pos().offset(second.getNormal())
        );
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return 0;
    }

    private boolean isConnectingSide(BlockPos a, BlockPos b, BlockState state) {
        Direction first = state.getValue(DIRECTION_A);
        Direction second = state.getValue(DIRECTION_B);

        Vec3i delta = b.subtract(a);
        Direction connection = Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        return (first.equals(connection) || second.equals(connection));
    }
}