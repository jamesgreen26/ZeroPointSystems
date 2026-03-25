package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.DodecaControllerBlockEntity;
import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DodecaControllerBlock extends CableComponentBlock implements EntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty BACK = BooleanProperty.create("back");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public DodecaControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(BACK, false)
                        .setValue(LEFT, false)
                        .setValue(RIGHT, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BACK, LEFT, RIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.DODECA_CONTROLLER.get().create(pos, state);
    }

    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, BlockHitResult arg6) {
        if (player.getItemInHand(hand).is(ModBlocks.DENSE_CABLES.get().asItem())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof DodecaControllerBlockEntity) {
            ((DodecaControllerBlockEntity) blockEntity).startRiding(player, false, pos, state, (ServerLevel) level);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public int getTotalChannelCount() {
        return 12;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        Direction facing = state.getValue(FACING);

        BlockPos back = self.offset(facing.getOpposite().getNormal());
        BlockPos left = self.offset(facing.getCounterClockWise().getNormal());
        BlockPos right = self.offset(facing.getClockWise().getNormal());

        if (from.equals(back) || from.equals(left) || from.equals(right)) {
            return 4;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        Direction facing = state.getValue(FACING);

        if (self.channel() >= Channels.DOD_I) {
            return List.of(self.pos().offset(facing.getOpposite().getNormal()));
        } else if (self.channel() >= Channels.DOD_E) {
            return List.of(self.pos().offset(facing.getCounterClockWise().getNormal()));
        } else if (self.channel() >= Channels.DOD_A) {
            return List.of(self.pos().offset(facing.getClockWise().getNormal()));
        }

        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        BlockState state = level.getBlockState(self);
        Direction facing = state.getValue(FACING);

        if (input.pos().equals(self.offset(facing.getClockWise().getNormal()))) {
            return input.channel() + 15;
        } else if (input.pos().equals(self.offset(facing.getCounterClockWise().getNormal()))) {
            return input.channel() + 19;
        } else if (input.pos().equals(self.offset(facing.getOpposite().getNormal()))) {
            return input.channel() + 23;
        }

        return Channels.MAIN;
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

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);

        boolean back = canConnect(pos, pos.offset(facing.getOpposite().getNormal()), level);
        boolean left = canConnect(pos, pos.offset(facing.getCounterClockWise().getNormal()), level);
        boolean right = canConnect(pos, pos.offset(facing.getClockWise().getNormal()), level);

        return state
                .setValue(BACK, back)
                .setValue(LEFT, left)
                .setValue(RIGHT, right);
    }
}
