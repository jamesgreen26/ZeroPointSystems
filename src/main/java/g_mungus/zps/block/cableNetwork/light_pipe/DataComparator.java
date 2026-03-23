package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.DataComparatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataComparator extends CableComponentBlock implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CONNECTED_A = BooleanProperty.create("connected_a");
    public static final BooleanProperty CONNECTED_B = BooleanProperty.create("connected_b");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<ComparisonMode> MODE = EnumProperty.create("comparison_mode", ComparisonMode.class);

    public DataComparator(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(CONNECTED_A, false).setValue(CONNECTED_B, false).setValue(POWERED, false).setValue(MODE, ComparisonMode.equals));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(FACING, CONNECTED_A, CONNECTED_B, POWERED, MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
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
        if (level instanceof ServerLevel && blockEntity instanceof DataComparatorBlockEntity comparator) {
            comparator.updateState();
        }
    }

    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult arg6) {
        if ((player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) || Compat.isCreateWrench(player.getItemInHand(hand).getItem())) {
            if (state.hasProperty(MODE)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);

                ComparisonMode next = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
                if (level instanceof ServerLevel && blockEntity instanceof DataComparatorBlockEntity comparator) {
                    comparator.updateState();
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        boolean aConnection = canConnect(pos, pos.relative(facing), level);
        boolean bConnection = canConnect(pos, pos.relative(facing.getOpposite()), level);
        return state.setValue(CONNECTED_A, aConnection).setValue(CONNECTED_B, bConnection);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public int getTotalChannelCount() {
        return 2;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        Vec3i normal = self.subtract(from);
        BlockState state = level.getBlockState(self);
        if (state.hasProperty(FACING)) {
            Direction facing = state.getValue(FACING);
            Direction connectionDir = Direction.fromDelta(normal.getX(), normal.getY(), normal.getZ());
            if (connectionDir == facing || connectionDir == facing.getOpposite()) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        Direction facing = state.getValue(FACING);

        if (self.channel() == Channels.PAIR_A) {
            return List.of(self.pos().relative(facing));
        } else if (self.channel() == Channels.PAIR_B) {
            return List.of(self.pos().relative(facing.getOpposite()));
        }

        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        BlockState state = level.getBlockState(self);
        Direction facing = state.getValue(FACING);
        Vec3i normal = self.subtract(input.pos());
        Direction inputDir = Direction.fromDelta(normal.getX(), normal.getY(), normal.getZ());
        if (inputDir == facing.getOpposite()) {
            return Channels.PAIR_A;
        } else {
            return Channels.PAIR_B;
        }
    }

    @Deprecated
    public int getSignal(BlockState arg, BlockGetter arg2, BlockPos arg3, Direction arg4) {
        return arg.getValue(POWERED) ? 15 : 0;
    }

    @Deprecated
    public int getDirectSignal(BlockState arg, BlockGetter arg2, BlockPos arg3, Direction arg4) {
        return arg.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos arg, BlockState arg2) {
        return new DataComparatorBlockEntity(arg, arg2);
    }

    public enum ComparisonMode implements StringRepresentable {
        equals("equals"), contains("contains"), greater_than("greater_than");

        private final String name;

        ComparisonMode(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        public ComparisonMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        public boolean compare(String a, String b) {
            return switch (this) {
                case equals -> a.equals(b);
                case contains -> a.contains(b);
                case greater_than -> {
                    try {
                        yield parseValue(a) > parseValue(b);
                    } catch (NumberFormatException e) {
                        yield false;
                    }
                }
            };
        }

        private static double parseValue(String s) {
            String[] parts = s.split(" ");
            if (parts.length == 0 || parts.length > 2) throw new NumberFormatException();
            String num = parts.length == 1 ? parts[0].replaceAll("[^0-9.\\-eE]+$", "") : parts[0];
            return Double.parseDouble(num);
        }
    }
}
