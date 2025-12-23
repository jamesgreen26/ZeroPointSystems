package g_mungus.zps.block;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ScriptTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScriptTransmitterBlock extends CableComponentBlock implements EntityBlock {
    public static BooleanProperty CONNECTED = BooleanProperty.create("connected");
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
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED, HAS_BOOK);
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
    public VoxelShape getOcclusionShape(BlockState arg, BlockGetter arg2, BlockPos arg3) {
        return Block.box(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 16.0F);
    }

    public boolean useShapeForLightOcclusion(BlockState arg) {
        return true;
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
        SHAPE_EAST = Shapes.or(Block.box(10.666667, 10.0F, 0.0F, 15.0F, 14.0F, 16.0F), Block.box(6.333333, 12.0F, 0.0F, 10.666667, 16.0F, 16.0F), Block.box(2.0F, 14.0F, 0.0F, 6.333333, 18.0F, 16.0F), baseEast);
        SHAPE_WEST = Shapes.or(Block.box(1.0F, 10.0F, 0.0F, 5.333333, 14.0F, 16.0F), Block.box(5.333333, 12.0F, 0.0F, 9.666667, 16.0F, 16.0F), Block.box(9.666667, 14.0F, 0.0F, 14.0F, 18.0F, 16.0F), baseWest);
    }
}
