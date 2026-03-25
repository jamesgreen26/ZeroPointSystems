package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import g_mungus.zps.networking.ScriptComputerS2CPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScriptTerminalBlock extends CableComponentBlock implements EntityBlock {


    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final VoxelShape SUPPORT_SHAPE = Shapes.join(Shapes.block(), Block.box(2.0, Math.max(2, 1), 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);


    public ScriptTerminalBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(CONNECTED, false).setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(CONNECTED, FACING, POWERED);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState arg, @NotNull Level arg2, @NotNull BlockPos arg3, @NotNull Block arg4, @NotNull BlockPos arg5, boolean bl) {
        super.neighborChanged(arg, arg2, arg3, arg4, arg5, bl);
        if (!arg2.isClientSide) {
            boolean powered = arg.getValue(POWERED);
            if (powered != arg2.hasNeighborSignal(arg3)) {
                arg2.setBlock(arg3, arg.cycle(POWERED), 2);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState arg, @NotNull BlockGetter arg2, @NotNull BlockPos arg3, @NotNull CollisionContext arg4) {
        return Block.box(0,0,0,16,10,16);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getBlockSupportShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return SUPPORT_SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

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
        if (from.equals(behind)) {
            return 1;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        if (self.channel() == Channels.MAIN) {
            BlockState state = level.getBlockState(self.pos());
            return List.of(self.pos().offset(state.getValue(FACING).getOpposite().getNormal()));
        }
        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction backDirection = state.getValue(FACING).getOpposite();
        boolean connected = canConnect(pos, pos.offset(backDirection.getNormal()), level);
        return state.setValue(CONNECTED, connected);
    }


    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new ScriptTerminalBlockEntity(arg, arg2);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ScriptTerminalBlockEntity it) {
                it.tick();
            }
        };
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull InteractionResult use(@NotNull BlockState arg, Level arg2, @NotNull BlockPos arg3, @NotNull Player arg4, @NotNull InteractionHand arg5, @NotNull BlockHitResult arg6) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof ScriptTerminalBlockEntity scriptComputer) {
            if (arg4 instanceof ServerPlayer serverPlayer) {
                ScriptComputerS2CPacket packet = new ScriptComputerS2CPacket(
                    arg3,
                    scriptComputer.getLoop(),
                    scriptComputer.getDelay(),
                    scriptComputer.getValue(),
                    scriptComputer.collectBlocks()
                );
                ZPSGamePackets.INSTANCE.sendTo(packet, serverPlayer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
            return InteractionResult.sidedSuccess(arg2.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }
}
