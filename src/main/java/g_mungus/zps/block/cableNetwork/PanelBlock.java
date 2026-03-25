package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PanelBlock extends CableComponentBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public static final VoxelShape NORTH_SHAPE;
    public static final VoxelShape EAST_SHAPE;
    public static final VoxelShape SOUTH_SHAPE;
    public static final VoxelShape WEST_SHAPE;
    public static final VoxelShape UP_SHAPE;
    public static final VoxelShape DOWN_SHAPE;



    public PanelBlock(Properties arg) {
        super(arg);
    }


    @Override
    public void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState)) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        }
    }

    @Override
    public boolean isTerminal() {
        return true;
    }


    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        return List.of(getConnectingPos(state, self.pos()));
    }

    protected BlockPos getConnectingPos(BlockState state, BlockPos pos) {
        Direction behind = switch (state.getValue(FACE)) {
            case WALL    -> state.getValue(FACING).getOpposite();
            case FLOOR   -> Direction.DOWN;
            case CEILING -> Direction.UP;
        };
        return pos.relative(behind);
    }

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean shouldConnect = canConnect(pos, getConnectingPos(state, pos), level);
        return state.setValue(CONNECTED, shouldConnect);
    }


    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(BlockState arg, @NotNull BlockGetter arg2, @NotNull BlockPos arg3, @NotNull CollisionContext arg4) {
        return switch (arg.getValue(FACE)) {
            case FLOOR -> DOWN_SHAPE;
            case WALL -> switch (arg.getValue(FACING)) {
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                default -> NORTH_SHAPE;
            };
            default -> UP_SHAPE;
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = this.defaultBlockState();

        Direction clickedFace = ctx.getNearestLookingDirection().getOpposite();
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();

        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
            clickedFace = clickedFace.getOpposite();
        }

        if (clickedFace == Direction.UP) {
            // Placed on top of a block → panel faces up
            return state
                    .setValue(FACE, AttachFace.FLOOR)
                    .setValue(FACING, horizontalFacing);
        }

        if (clickedFace == Direction.DOWN) {
            // Placed under a block → panel faces down
            return state
                    .setValue(FACE, AttachFace.CEILING)
                    .setValue(FACING, horizontalFacing);
        }

        // Otherwise it's a wall placement
        return state
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, clickedFace);
    }




    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    protected boolean isFrontFace(BlockState state, Direction clicked) {
        AttachFace face = state.getValue(FACE);

        return switch (face) {
            case WALL     -> clicked == state.getValue(FACING);
            case FLOOR    -> clicked == Direction.UP;
            case CEILING  -> clicked == Direction.DOWN;
        };
    }


    protected int getQuadrant(BlockState state, double x, double y, double z) {
        double u, v;

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        switch (face) {
            case WALL -> {
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = y; }
                    case SOUTH -> { u = x;     v = y; }
                    case WEST  -> { u = z;     v = y; }
                    case EAST  -> { u = 1 - z; v = y; }
                    default -> { return -1; }
                }
            }
            case FLOOR -> {
                // Front face is horizontal, facing *facing*
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = z;     }
                    case SOUTH -> { u = x;     v = 1 - z; }
                    case WEST  -> { u = z;     v = x;     }
                    case EAST  -> { u = 1 - z; v = 1 - x; }
                    default -> { return -1; }
                }
            }

            case CEILING -> {
                // Same as FLOOR but vertically mirrored
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = 1 - z; }
                    case SOUTH -> { u = x;     v = z;     }
                    case WEST  -> { u = z;     v = 1 - x; }
                    case EAST  -> { u = 1 - z; v = x;     }
                    default -> { return -1; }
                }
            }
            default -> { return -1; }
        }

        // Clamp just in case
        u = Mth.clamp(u, 0.0, 1.0);
        v = Mth.clamp(v, 0.0, 1.0);

        // Quadrants:
        // 0 | 1
        // -----
        // 2 | 3
        if (v > 0.5) {
            return u < 0.5 ? 0 : 1;
        } else {
            return u < 0.5 ? 2 : 3;
        }
    }



    static {
        NORTH_SHAPE = Block.box(0, 0, 8, 16, 16, 16);
        SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 8);
        WEST_SHAPE  = Block.box(8, 0, 0, 16, 16, 16);
        EAST_SHAPE  = Block.box(0, 0, 0, 8, 16, 16);
        UP_SHAPE    = Block.box(0, 8, 0, 16, 16, 16);
        DOWN_SHAPE  = Block.box(0, 0, 0, 16, 8, 16);
    }
}
