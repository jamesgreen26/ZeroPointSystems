package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.blockentity.light_pipe.LidarScannerBlockEntity;
import g_mungus.zps.blockentity.light_pipe.LightPipeDataSender;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LidarScannerBlock extends CableComponentBlock implements EntityBlock {

    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LidarScannerBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(CONNECTED, false).setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(CONNECTED, FACING, POWERED);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
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
    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public int getTotalChannelCount() {
        return 1;
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
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        BlockPos behind = self.offset(state.getValue(FACING).getOpposite().getNormal());
        return from.equals(behind) ? 1 : 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        if (self.channel() != Channels.MAIN) {
            return List.of();
        }
        BlockState state = level.getBlockState(self.pos());
        return List.of(self.pos().offset(state.getValue(FACING).getOpposite().getNormal()));
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }

    public @NotNull BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction backDirection = state.getValue(FACING).getOpposite();
        boolean connected = canConnect(pos, pos.offset(backDirection.getNormal()), level);
        return state.setValue(CONNECTED, connected);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);

        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LightPipeDataSender sender) {
                sender.onRemove(level);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new LidarScannerBlockEntity(arg, arg2);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof LidarScannerBlockEntity it) {
                it.tick();
            }
        };
    }
}
