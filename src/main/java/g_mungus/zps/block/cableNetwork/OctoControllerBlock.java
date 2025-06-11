package g_mungus.zps.block.cableNetwork;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.block.cableNetwork.core.CableNetworkComponent;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.OctoControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OctoControllerBlock extends Block implements EntityBlock, CableNetworkComponent {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static BooleanProperty BACK = BooleanProperty.create("back");
    public static BooleanProperty DOWN = BooleanProperty.create("down");

    public OctoControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(BACK, false)
                        .setValue(DOWN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BACK, DOWN);
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
        return ModBlockEntities.OCTO_CONTROLLER.get().create(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack arg, BlockState arg2, Level arg3, BlockPos arg4, Player arg5, InteractionHand arg6, BlockHitResult arg7) {
        InteractionResult result = useWithoutItem(arg2, arg3, arg4, arg5, arg7);
        if (result == InteractionResult.CONSUME) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult arg5) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof OctoControllerBlockEntity) {
            ((OctoControllerBlockEntity) blockEntity).startRiding(player, false, pos, state, (ServerLevel) level);
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
        return 8;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        BlockPos behind = self.offset(state.getValue(FACING).getOpposite().getNormal());
        if (from.equals(behind) || from.equals(self.below())) {
            return 4;
        } else {
            return 0;
        }
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        if (self.channel() >= Channels.OCT_E) {
            BlockState state = level.getBlockState(self.pos());
            return List.of(self.pos().offset(state.getValue(FACING).getOpposite().getNormal()));
        } else if (self.channel() >= Channels.OCT_A) {
            return List.of(self.pos().below());
        } else {
            return List.of();
        }
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        if (input.pos().equals(self.below())) {
            return input.channel() + 4;
        } else {
            return input.channel() + 8;
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

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction backDirection = state.getValue(FACING).getOpposite();

        boolean back = canConnect(pos, pos.offset(backDirection.getNormal()), level);
        boolean down = canConnect(pos, pos.offset(Direction.DOWN.getNormal()), level);

        return state
                .setValue(BACK, back)
                .setValue(DOWN, down);
    }
}
