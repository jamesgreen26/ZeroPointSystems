package g_mungus.block.cableNetwork;

import g_mungus.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public class DenseCableBend extends Block implements QuadCableNetworkConnector {
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
                .setValue(DIRECTION_B, directions[1])
                .setValue(CONNECTIONS, getConnections(directions[0], directions[1], context.getClickedPos(), context.getLevel()));
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
            int currentConnections = state.getValue(CONNECTIONS);
            Direction dirA = state.getValue(DIRECTION_A);
            Direction dirB = state.getValue(DIRECTION_B);
            int newConnections = getConnections(dirA, dirB, pos, level);

            if (currentConnections != newConnections) {
                level.setBlock(pos, state.setValue(CONNECTIONS, newConnections), 3);

                BlockPos neighborA = pos.offset(dirA.getNormal().multiply(-1));
                BlockPos neighborB = pos.offset(dirB.getNormal().multiply(-1));

                BlockPos componentA = getConnectedComponent(pos, neighborA, state, level, 0);

                if (componentA != null) {
                    BlockState componentState = level.getBlockState(componentA);
                    Block component = componentState.getBlock();

                    if (component instanceof CableNetworkComponent) {
                        ((CableNetworkComponent) component).updateNetwork(componentA, level);
                        return;
                    }
                }

                BlockPos componentB = getConnectedComponent(pos, neighborB, state, level, 0);

                if (componentB != null) {
                    BlockState componentState = level.getBlockState(componentB);
                    Block component = componentState.getBlock();

                    if (component instanceof CableNetworkComponent) {
                        ((CableNetworkComponent) component).updateNetwork(componentB, level);
                    }
                }
            }
        }
    }

    @Override
    public @Nullable BlockPos getConnectedComponent(BlockPos self, BlockPos from, BlockState selfState, Level level, int recursion) {
        if (recursion > 256) return null;

        if (selfState.is(ModBlocks.DENSE_CABLE_BEND.get())) {
            Direction dirA = selfState.getValue(DIRECTION_A);
            Direction dirB = selfState.getValue(DIRECTION_B);

            BlockPos to;
            if (from.equals(self.offset(dirA.getNormal()))) {
                to = self.offset(dirB.getNormal());
            } else {
                to = self.offset(dirA.getNormal());
            }

            BlockState toState = level.getBlockState(to);
            Block toBlock = toState.getBlock();

            if (toBlock instanceof QuadCableNetworkAble && ((QuadCableNetworkAble) toBlock).canQuadConnectTo(to, self, toState)) {
                if (toBlock instanceof QuadCableNetworkConnector) {
                    return ((QuadCableNetworkConnector) toBlock).getConnectedComponent(to, self, toState, level, recursion + 1);
                } else if (toBlock instanceof QuadCableNetworkComponent) {
                    return to;
                }
            }
        }
        return null;
    }

    int getConnections(Direction dirA, Direction dirB, BlockPos pos, Level level) {
        int result = 0;

        BlockPos neighborA = pos.offset(dirA.getNormal());
        BlockPos neighborB = pos.offset(dirB.getNormal());

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
        if (selfState.is(ModBlocks.DENSE_CABLE_BEND.get())) {
            Direction dirA = selfState.getValue(DIRECTION_A);
            Direction dirB = selfState.getValue(DIRECTION_B);

            return to.equals(self.offset(dirA.getNormal())) || 
                   to.equals(self.offset(dirB.getNormal()));
        }
        return false;
    }
} 