package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CableBlock extends CableComponentBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty INSULATED = BooleanProperty.create("insulated");

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 0, 10, 10, 6);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 10, 10, 10, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(10, 6, 6, 16, 10, 10);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 6, 6, 6, 10, 10);
    private static final VoxelShape UP_SHAPE = Block.box(6, 10, 6, 10, 16, 10);
    private static final VoxelShape DOWN_SHAPE = Block.box(6, 0, 6, 10, 6, 10);

    public CableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(INSULATED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, INSULATED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(INSULATED)) {
            return Shapes.block();
        }

        VoxelShape shape = CORE;

        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_SHAPE);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_SHAPE);

        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos blockPos, Player arg4, InteractionHand arg5, BlockHitResult arg6) {
        if (!state.getValue(INSULATED) && arg4.getItemInHand(arg5).is(ModItems.CABLE_INSULATION.get())) {
            if (!level.isClientSide()) {
                level.setBlock(blockPos, state.setValue(INSULATED, true), 3);
            } else {
                net.minecraft.world.level.block.SoundType soundType = state.getSoundType();
                level.playSound(arg4, blockPos, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            if (!arg4.isCreative()) {
                arg4.getItemInHand(arg5).shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else {
            return super.use(state, level, blockPos, arg4, arg5, arg6);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext arg) {
        BlockState blockState = this.defaultBlockState();
        return getNewBlockState(blockState, arg.getLevel(), arg.getClickedPos());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.hasProperty(INSULATED) && state.getValue(INSULATED)) {
            newState = state.setValue(INSULATED, false);
            level.setBlock(pos, newState, 3);
            popResource(level, pos, new ItemStack(ModItems.CABLE_INSULATION.get(), 1));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player)
    {
        if (state.hasProperty(INSULATED) && state.getValue(INSULATED) && !player.isShiftKeyDown()) {
            return new ItemStack(ModItems.CABLE_INSULATION.get(), 1);
        } else {
            return getCloneItemStack(level, pos, state);
        }
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.DEFAULT;
    }

    @Override
    public void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState) && !state.getValue(INSULATED)) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        } else if (state.getValue(INSULATED)) {
            updateNetwork(pos, level);
        }
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public int getTotalChannelCount() {
        return 1;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        if (state.hasProperty(INSULATED) && state.getValue(INSULATED)) {
            Vec3i n = from.subtract(self);
            Direction direction = Direction.fromDelta(n.getX(), n.getY(), n.getZ());

            boolean shouldConnect = false;

            if (direction == Direction.NORTH) {
                shouldConnect = state.getValue(NORTH);
            } else if (direction == Direction.SOUTH) {
                shouldConnect = state.getValue(SOUTH);
            } else if (direction == Direction.EAST) {
                shouldConnect = state.getValue(EAST);
            } else if (direction == Direction.WEST) {
                shouldConnect = state.getValue(WEST);
            } else if (direction == Direction.UP) {
                shouldConnect = state.getValue(UP);
            } else if (direction == Direction.DOWN) {
                shouldConnect = state.getValue(DOWN);
            }

            if (!shouldConnect) {
                return 0;
            }
        }
        return getTotalChannelCount();
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockPos origin = self.pos();
        return new ArrayList<>(List.of(
                origin.above(),
                origin.below(),
                origin.north(),
                origin.east(),
                origin.south(),
                origin.west()
        ));
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }


    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean north = canConnect(pos, pos.offset(Direction.NORTH.getNormal()), level);
        boolean south = canConnect(pos, pos.offset(Direction.SOUTH.getNormal()), level);
        boolean east = canConnect(pos, pos.offset(Direction.EAST.getNormal()), level);
        boolean west = canConnect(pos, pos.offset(Direction.WEST.getNormal()), level);
        boolean up = canConnect(pos, pos.offset(Direction.UP.getNormal()), level);
        boolean down = canConnect(pos, pos.offset(Direction.DOWN.getNormal()), level);

        return state
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }
}