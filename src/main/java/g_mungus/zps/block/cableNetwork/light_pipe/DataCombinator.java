package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.DataCombinatorBlockEntity;
import g_mungus.zps.blockentity.light_pipe.LightPipeDataSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataCombinator extends CableComponentBlock implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CONNECTED_A = BooleanProperty.create("connected_a");
    public static final BooleanProperty CONNECTED_B = BooleanProperty.create("connected_b");
    public static final BooleanProperty CONNECTED_OUT = BooleanProperty.create("connected_out");
    public static final EnumProperty<CombineMode> MODE = EnumProperty.create("combine_mode", CombineMode.class);

    public DataCombinator(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(CONNECTED_A, false)
                .setValue(CONNECTED_B, false)
                .setValue(CONNECTED_OUT, false)
                .setValue(MODE, CombineMode.append));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(FACING, CONNECTED_A, CONNECTED_B, CONNECTED_OUT, MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
    public void onPlace(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(newState, level, pos, oldState, moved);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (level instanceof ServerLevel && blockEntity instanceof DataCombinatorBlockEntity combinator) {
            combinator.refreshOutput();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LightPipeDataSender sender) {
                sender.onRemove(level);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult arg6) {
        if ((player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) || Compat.isCreateWrench(player.getItemInHand(hand).getItem())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CombineMode next = state.getValue(MODE).next();
            level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
            if (level instanceof ServerLevel && blockEntity instanceof DataCombinatorBlockEntity combinator) {
                combinator.refreshOutput();
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        boolean aConnection = canConnect(pos, pos.relative(facing.getCounterClockWise()), level);
        boolean bConnection = canConnect(pos, pos.relative(facing.getClockWise()), level);
        boolean outConnection = canConnect(pos, pos.relative(facing), level);
        return state
                .setValue(CONNECTED_A, aConnection)
                .setValue(CONNECTED_B, bConnection)
                .setValue(CONNECTED_OUT, outConnection);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public int getTotalChannelCount() {
        return 3;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        if (!state.hasProperty(FACING)) return 0;
        Direction facing = state.getValue(FACING);
        Vec3i normal = self.subtract(from);
        Direction connectionDir = Direction.fromDelta(normal.getX(), normal.getY(), normal.getZ());
        if (connectionDir == facing.getOpposite()
                || connectionDir == facing.getClockWise()
                || connectionDir == facing.getCounterClockWise()) {
            return 1;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        Direction facing = state.getValue(FACING);

        if (self.channel() == Channels.TRIPLE_A) {
            return List.of(self.pos().relative(facing.getCounterClockWise()));
        } else if (self.channel() == Channels.TRIPLE_B) {
            return List.of(self.pos().relative(facing.getClockWise()));
        } else if (self.channel() == Channels.TRIPLE_C) {
            return List.of(self.pos().relative(facing));
        }

        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        BlockState state = level.getBlockState(self);
        Direction facing = state.getValue(FACING);
        Vec3i normal = self.subtract(input.pos());
        Direction inputDir = Direction.fromDelta(normal.getX(), normal.getY(), normal.getZ());
        if (inputDir == facing.getClockWise()) {
            return Channels.TRIPLE_A;
        } else if (inputDir == facing.getCounterClockWise()) {
            return Channels.TRIPLE_B;
        }
        return Channels.TRIPLE_C;
    }

    @Override
    public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new DataCombinatorBlockEntity(arg, arg2);
    }

    public enum CombineMode implements StringRepresentable {
        append("append"), replace("replace");

        private final String name;

        CombineMode(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        public CombineMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}
