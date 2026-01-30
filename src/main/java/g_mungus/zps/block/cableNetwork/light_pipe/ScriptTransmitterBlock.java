package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.Compat;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.core.*;
import g_mungus.zps.blockentity.light_pipe.LightPipeDataSender;
import g_mungus.zps.blockentity.light_pipe.ScriptTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScriptTransmitterBlock extends LecternBlock implements EntityBlock, CableNetworkComponent {
    public static BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static BooleanProperty POWERED = LecternBlock.POWERED;
    public static BooleanProperty HAS_BOOK = BlockStateProperties.HAS_BOOK;
    public static DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final VoxelShape SHAPE_NORTH;
    public static final VoxelShape SHAPE_SOUTH;
    public static final VoxelShape SHAPE_EAST;
    public static final VoxelShape SHAPE_WEST;

    public ScriptTransmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HAS_BOOK, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED, false)
                .setValue(POWERED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED, HAS_BOOK, POWERED);
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(HAS_BOOK)) {
                this.popBook(state, level, pos);
            }

            if (state.getValue(POWERED)) {
                level.updateNeighborsAt(pos.below(), this);
            }

            super.onRemove(state, level, pos, newState, moved);
        }

        updateSelfAndNeighbors(newState, level, pos, state);

        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LightPipeDataSender sender) {
                sender.onRemove(level);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(newState, level, pos, oldState, moved);

        updateSelfAndNeighbors(newState, level, pos, oldState);
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
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ScriptTransmitterBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public InteractionResult use(BlockState arg, Level arg2, BlockPos arg3, Player arg4, InteractionHand arg5, BlockHitResult arg6) {
        if (arg.getValue(HAS_BOOK)) {
            if (!arg2.isClientSide) {
                if (Compat.isCreateDeployer(arg4) && arg4.getItemInHand(arg5).isEmpty()) {
                    BlockEntity blockEntity = arg2.getBlockEntity(arg3);
                    if (blockEntity instanceof ScriptTransmitterBlockEntity transmitter) {
                        if (transmitter.hasNextPage()) {
                            transmitter.turnPage();
                        } else {
                            ItemStack itemStack = transmitter.getBook().copy();
                            transmitter.clearContent();
                            transmitter.onBookItemRemove();
                            arg4.getInventory().add(itemStack);
                        }
                    }
                }
                this.openScreen(arg2, arg3, arg4);
            }

            return InteractionResult.sidedSuccess(arg2.isClientSide);
        } else {
            ItemStack itemStack = arg4.getItemInHand(arg5);
            return !itemStack.isEmpty() && !itemStack.is(ItemTags.LECTERN_BOOKS) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
    }

    private void openScreen(Level arg, BlockPos arg2, Player arg3) {
        BlockEntity blockEntity = arg.getBlockEntity(arg2);
        if (blockEntity instanceof ScriptTransmitterBlockEntity transmitterBlockEntity) {
            arg3.openMenu(transmitterBlockEntity);
            arg3.awardStat(Stats.INTERACT_WITH_LECTERN);
        }
    }

    private void popBook(BlockState arg, Level arg2, BlockPos arg3) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof ScriptTransmitterBlockEntity transmitterBlockEntity) {
            Direction direction = arg.getValue(FACING);
            ItemStack itemStack = transmitterBlockEntity.getBook().copy();
            float f = 0.25F * (float)direction.getStepX();
            float g = 0.25F * (float)direction.getStepZ();
            ItemEntity itemEntity = new ItemEntity(arg2, (double)arg3.getX() + (double)0.5F + (double)f, (double)(arg3.getY() + 1), (double)arg3.getZ() + (double)0.5F + (double)g, itemStack);
            itemEntity.setDefaultPickUpDelay();
            arg2.addFreshEntity(itemEntity);
            transmitterBlockEntity.clearContent();
        }

    }

    public int getAnalogOutputSignal(BlockState arg, Level arg2, BlockPos arg3) {
        if (arg.getValue(HAS_BOOK)) {
            BlockEntity blockEntity = arg2.getBlockEntity(arg3);
            if (blockEntity instanceof ScriptTransmitterBlockEntity) {
                return ((ScriptTransmitterBlockEntity)blockEntity).getRedstoneSignal();
            }
        }

        return 0;
    }


    public static boolean tryPlaceBook(Entity arg, Level arg2, BlockPos arg3, BlockState arg4, ItemStack arg5) {
        if (!(Boolean)arg4.getValue(HAS_BOOK)) {
            if (!arg2.isClientSide) {
                placeBook(arg, arg2, arg3, arg4, arg5);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBook(Entity arg, Level arg2, BlockPos arg3, BlockState arg4, ItemStack arg5) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof ScriptTransmitterBlockEntity be) {
            be.setBook(arg5.split(1));
            resetBookState(arg, arg2, arg3, arg4, true);
            arg2.playSound((Player)null, arg3, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    static {
        // Base platform (same for all rotations)
        VoxelShape base = Block.box(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 16.0F);

        // NORTH facing: main body and back connector at Z=4-13 and Z=13-16
        VoxelShape postNorth = Block.box(2.0F, 2.0F, 4.0F, 14.0F, 15.0F, 13.0F);
        VoxelShape backNorth = Block.box(4.0F, 4.0F, 13.0F, 12.0F, 12.0F, 16.0F);
        VoxelShape baseNorth = Shapes.or(base, postNorth, backNorth);

        // SOUTH facing (180° rotation): main body and back connector at Z=3-12 and Z=0-3
        VoxelShape postSouth = Block.box(2.0F, 2.0F, 3.0F, 14.0F, 15.0F, 12.0F);
        VoxelShape backSouth = Block.box(4.0F, 4.0F, 0.0F, 12.0F, 12.0F, 3.0F);
        VoxelShape baseSouth = Shapes.or(base, postSouth, backSouth);

        // EAST facing (90° CW): main body and back connector at X=4-13 and X=13-16
        VoxelShape postEast = Block.box(4.0F, 2.0F, 2.0F, 13.0F, 15.0F, 14.0F);
        VoxelShape backEast = Block.box(13.0F, 4.0F, 4.0F, 16.0F, 12.0F, 12.0F);
        VoxelShape baseEast = Shapes.or(base, postEast, backEast);

        // WEST facing (90° CCW): main body and back connector at X=3-12 and X=0-3
        VoxelShape postWest = Block.box(3.0F, 2.0F, 2.0F, 12.0F, 15.0F, 14.0F);
        VoxelShape backWest = Block.box(0.0F, 4.0F, 4.0F, 3.0F, 12.0F, 12.0F);
        VoxelShape baseWest = Shapes.or(base, postWest, backWest);

        // Directional antenna shapes (rotated 22.5°)
        SHAPE_NORTH = Shapes.or(Block.box(0.0F, 10.0F, 1.0F, 16.0F, 14.0F, 5.333333), Block.box(0.0F, 12.0F, 5.333333, 16.0F, 16.0F, 9.666667), Block.box(0.0F, 14.0F, 9.666667, 16.0F, 18.0F, 14.0F), baseNorth);
        SHAPE_SOUTH = Shapes.or(Block.box(0.0F, 10.0F, 10.666667, 16.0F, 14.0F, 15.0F), Block.box(0.0F, 12.0F, 6.333333, 16.0F, 16.0F, 10.666667), Block.box(0.0F, 14.0F, 2.0F, 16.0F, 18.0F, 6.333333), baseSouth);
        SHAPE_EAST = Shapes.or(Block.box(10.666667, 10.0F, 0.0F, 15.0F, 14.0F, 16.0F), Block.box(6.333333, 12.0F, 0.0F, 10.666667, 16.0F, 16.0F), Block.box(2.0F, 14.0F, 0.0F, 6.333333, 18.0F, 16.0F), baseWest);
        SHAPE_WEST = Shapes.or(Block.box(1.0F, 10.0F, 0.0F, 5.333333, 14.0F, 16.0F), Block.box(5.333333, 12.0F, 0.0F, 9.666667, 16.0F, 16.0F), Block.box(9.666667, 14.0F, 0.0F, 14.0F, 18.0F, 16.0F), baseEast);
    }
}
