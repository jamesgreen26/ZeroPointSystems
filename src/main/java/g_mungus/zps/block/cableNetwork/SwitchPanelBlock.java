package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.SwitchPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SwitchPanelBlock extends CableComponentBlock implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty POWERED_0 = BooleanProperty.create("powered_0");
    public static final BooleanProperty POWERED_1 = BooleanProperty.create("powered_1");
    public static final BooleanProperty POWERED_2 = BooleanProperty.create("powered_2");
    public static final BooleanProperty POWERED_3 = BooleanProperty.create("powered_3");
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public static final VoxelShape NORTH_SHAPE;
    public static final VoxelShape EAST_SHAPE;
    public static final VoxelShape SOUTH_SHAPE;
    public static final VoxelShape WEST_SHAPE;
    public static final VoxelShape UP_SHAPE;
    public static final VoxelShape DOWN_SHAPE;

    public SwitchPanelBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(POWERED_0, false)
                .setValue(POWERED_1, false)
                .setValue(POWERED_2, false)
                .setValue(POWERED_3, false)
                .setValue(CONNECTED, false)
        );
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        arg.add(FACE, FACING, POWERED_0, POWERED_1, POWERED_2, POWERED_3, CONNECTED);
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.DEFAULT;
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
    public int getTotalChannelCount() {
        return 4;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        return getConnectingPos(state, self).equals(from) ? 4 : 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        return List.of(getConnectingPos(state, self.pos()));
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.toQuad(input.channel());
    }

    private BlockPos getConnectingPos(BlockState state, BlockPos pos) {
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

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new SwitchPanelBlockEntity(arg, arg2);
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

        Direction clickedFace = ctx.getClickedFace();
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();

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
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!isFrontFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // World → block-local (0..1)
        Vec3 hitPos = hit.getLocation();
        double x = hitPos.x - pos.getX();
        double y = hitPos.y - pos.getY();
        double z = hitPos.z - pos.getZ();

        int quadrant = getQuadrant(state, x, y, z);

        if (quadrant == -1) {
            return InteractionResult.PASS;
        }

        BooleanProperty prop = switch (quadrant) {
            case 0 -> POWERED_0;
            case 1 -> POWERED_1;
            case 2 -> POWERED_2;
            case 3 -> POWERED_3;
            default -> throw new IllegalStateException();
        };

        level.setBlock(
                pos,
                state.cycle(prop),
                Block.UPDATE_ALL
        );

        level.playSound(
                null,
                pos,
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                state.getValue(prop) ? 0.5F : 0.6F
        );

        level.updateNeighborsAt(pos, this);
        updateSignal(level, pos, quadrant + 1); // Quad channels are 1 through 4

        return InteractionResult.CONSUME;
    }

    private static void updateSignal(@NotNull Level level, @NotNull BlockPos pos, int channel) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SwitchPanelBlockEntity switchPanelBlockEntity) {
            switchPanelBlockEntity.updateSignal(level, channel);
        }
    }

    private static boolean isFrontFace(BlockState state, Direction clicked) {
        AttachFace face = state.getValue(FACE);

        return switch (face) {
            case WALL     -> clicked == state.getValue(FACING);
            case FLOOR    -> clicked == Direction.UP;
            case CEILING  -> clicked == Direction.DOWN;
        };
    }


    private static int getQuadrant(BlockState state, double x, double y, double z) {
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
