package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import g_mungus.zps.blockentity.light_pipe.ScriptComparatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScriptComparator extends CableComponentBlock implements EntityBlock {

    public static EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 2);
    public static BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static EnumProperty<ComparisonMode> MODE = EnumProperty.create("comparison_mode", ComparisonMode.class);

    public ScriptComparator(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(CONNECTIONS, 0).setValue(POWERED, false).setValue(MODE, ComparisonMode.equals));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(AXIS, CONNECTIONS, POWERED, MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return this.defaultBlockState().setValue(AXIS, facing.getAxis());
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
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult arg6) {
        if ((player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) ||
                BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).equals(ResourceLocation.fromNamespaceAndPath("create", "wrench"))
        ) {
            if (state.hasProperty(MODE)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);

                ComparisonMode next = state.getValue(MODE).next();
                level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
                if (level instanceof ServerLevel && blockEntity instanceof ScriptComparatorBlockEntity comparator) {
                    comparator.updateState();
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction.Axis currentAxis = state.getValue(AXIS);
        Direction positiveDir = Direction.fromAxisAndDirection(currentAxis, Direction.AxisDirection.POSITIVE);
        Direction negativeDir = Direction.fromAxisAndDirection(currentAxis, Direction.AxisDirection.NEGATIVE);

        boolean posConnection = canConnect(pos, pos.offset(positiveDir.getNormal()), level);
        boolean negConnection = canConnect(pos, pos.offset(negativeDir.getNormal()), level);

        int connectionCount = (posConnection ? 1 : 0) + (negConnection ? 1 : 0);

        return state.setValue(CONNECTIONS, connectionCount);
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
        if (state.hasProperty(AXIS) && state.getValue(AXIS).test(Direction.fromDelta(normal.getX(), normal.getY(), normal.getZ()))) {
            return 1;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        Direction.Axis currentAxis = state.getValue(AXIS);

        if (self.channel() == Channels.PAIR_A) {
            Direction positiveDir = Direction.fromAxisAndDirection(currentAxis, Direction.AxisDirection.POSITIVE);
            return List.of(self.pos().offset(positiveDir.getNormal()));
        } else if (self.channel() == Channels.PAIR_B) {
            Direction negativeDir = Direction.fromAxisAndDirection(currentAxis, Direction.AxisDirection.NEGATIVE);
            return List.of(self.pos().offset(negativeDir.getNormal()));
        }

        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        Vec3i normal = self.subtract(input.pos());
        if (normal.getX() + normal.getY() + normal.getZ() < 0) {
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
        return new ScriptComparatorBlockEntity(arg, arg2);
    }

    public enum ComparisonMode implements StringRepresentable {
        equals("equals"), contains("contains");

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
                case contains -> a.contains(b) || b.contains(a);
            };
        }
    }
}
