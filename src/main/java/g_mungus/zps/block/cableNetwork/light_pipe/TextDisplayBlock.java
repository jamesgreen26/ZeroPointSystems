package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextDisplayBlock extends CableComponentBlock implements EntityBlock {

    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DisplayLayout> LAYOUT = EnumProperty.create("layout", DisplayLayout.class);


    public TextDisplayBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(CONNECTED, false).setValue(FACING, Direction.NORTH).setValue(LAYOUT, DisplayLayout.SINGLE_1x1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(CONNECTED, FACING, LAYOUT);
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
    public boolean hasAnalogOutputSignal(BlockState arg) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState arg, Level arg2, BlockPos arg3) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof TextDisplayBlockEntity it) {
            return Math.min((int) Math.ceil((15 * ((double)it.getDisplayText().length()) / ((double) it.getMaxLength()))), 15);
        }
        return 0;
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
    public @Nullable BlockEntity newBlockEntity(BlockPos arg, BlockState arg2) {
        return new TextDisplayBlockEntity(arg, arg2);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        DisplayLayout layout = state.getValue(LAYOUT);

        Vec3i facing = state.getValue(FACING).getNormal();
        Vec3i left = facing.cross(Direction.UP.getNormal());

        // All positions we are allowed to connect to
        Set<BlockPos> connectable =
                new HashSet<>(getConnectableDisplayPositions(pos, layout, left, facing));

        // Positions that will form the merged display
        Set<BlockPos> merged = new HashSet<>(getExistingDisplayPositions(pos, layout, left));

        for (BlockPos candidatePos : connectable) {
            BlockState other = level.getBlockState(candidatePos);

            // Basic eligibility
            if (!other.is(state.getBlock())) continue;
            if (!other.hasProperty(FACING)) continue;
            if (other.getValue(FACING) != state.getValue(FACING)) continue;
            if (!other.hasProperty(LAYOUT)) continue;

            DisplayLayout otherLayout = other.getValue(LAYOUT);

            // Get all blocks that the neighbor already occupies
            List<BlockPos> otherExisting =
                    getExistingDisplayPositions(candidatePos, otherLayout, left);

            // Safety rule:
            // ALL of the neighbor's blocks must be within our connectable region
            boolean fullyContained = true;
            for (BlockPos p : otherExisting) {
                if (!connectable.contains(p)) {
                    fullyContained = false;
                    break;
                }
            }

            if (!fullyContained) continue;

            merged.addAll(otherExisting);
        }

        merged.removeIf(p -> {
            BlockState s = level.getBlockState(p);
            return !s.is(state.getBlock())
                    || !s.hasProperty(FACING)
                    || s.getValue(FACING) != state.getValue(FACING);
        });
        LayoutResult layoutResult;
        boolean needsRecalculation;

        do {
            needsRecalculation = false;
            layoutResult = resolveLayoutFromPositions(pos, merged, left);
            for(BlockPos block : layoutResult.blocks) {
                if (block.equals(pos)) continue;
                BlockState blockState = level.getBlockState(block);
                DisplayLayout displayLayout = blockState.getValue(LAYOUT);
                List<BlockPos> positions = getExistingDisplayPositions(block, displayLayout, left);
                if (!layoutResult.blocks.containsAll(positions)) {
                    positions.forEach(merged::remove);
                    merged.add(pos);
                    needsRecalculation = true;
                }
            }
        } while (needsRecalculation);

        return state.setValue(LAYOUT, layoutResult.layout);
    }


    List<BlockPos> getExistingDisplayPositions(BlockPos pos, DisplayLayout layout, Vec3i left) {
        return switch (layout) {
            case SINGLE_1x1 -> List.of(pos);
            case LEFT_2x1 -> List.of(pos, pos.subtract(left));
            case RIGHT_2x1 -> List.of(pos, pos.offset(left));
            case TOP_1x2 -> List.of(pos, pos.below());
            case BOTTOM_1x2 -> List.of(pos, pos.above());
            case TOP_LEFT_2x2 -> List.of(pos, pos.subtract(left), pos.below(), pos.below().subtract(left));
            case TOP_RIGHT_2x2 -> List.of(pos, pos.offset(left), pos.below(), pos.below().offset(left));
            case BOTTOM_LEFT_2x2 -> List.of(pos, pos.subtract(left), pos.above(), pos.above().subtract(left));
            case BOTTOM_RIGHT_2x2 -> List.of(pos, pos.offset(left), pos.above(), pos.above().offset(left));
        };
    }

    List<BlockPos> getConnectableDisplayPositions(BlockPos pos, DisplayLayout layout, Vec3i left, Vec3i facing) {
        if (layout.blockDimensions.x() == 2 && layout.blockDimensions.y() == 2) {
            return List.of(pos);
        } else if (layout == DisplayLayout.SINGLE_1x1) {
            BlockPos leftPos = pos.offset(left);
            BlockPos rightPos = pos.subtract(left);

            return List.of(
                    pos, leftPos, leftPos.above(), pos.above(), rightPos.above(), rightPos, rightPos.below(), pos.below(), leftPos.below()
            );
        } else {
            Vec3i mainOffset = switch (layout) {
                case TOP_1x2 -> Direction.DOWN.getNormal();
                case BOTTOM_1x2 -> Direction.UP.getNormal();
                case LEFT_2x1 -> left.multiply(-1);
                case RIGHT_2x1 -> left;
                default -> throw new IllegalStateException();
            };

            Vec3i offOffset = mainOffset.cross(facing);
            BlockPos offA = pos.offset(offOffset);
            BlockPos offB = pos.subtract(offOffset);
            return List.of(pos, pos.offset(mainOffset), offA, offA.offset(mainOffset), offB, offB.offset(mainOffset));
        }
    }

    private LayoutResult resolveLayoutFromPositions(BlockPos origin, Set<BlockPos> positions, Vec3i left) {
        BlockPos leftPos = origin.offset(left);
        BlockPos rightPos = origin.subtract(left);

        // All possible 2x2 corners relative to this block
        BlockPos[] corners = new BlockPos[] {
                origin, leftPos, leftPos.above(), origin.above(), rightPos.above(), rightPos, rightPos.below(), origin.below(), leftPos.below()
        };

        for (BlockPos corner : corners) {
            Set<BlockPos> square = Set.of(corner, corner.offset(left), corner.below(), corner.below().offset(left));
            if (positions.containsAll(square)) {
                DisplayLayout layout;
                if (corner.equals(origin)) {
                    layout = DisplayLayout.TOP_RIGHT_2x2;
                } else if (corner.equals(origin.subtract(left))) {
                    layout = DisplayLayout.TOP_LEFT_2x2;
                } else if (corner.equals(origin.above())) {
                    layout = DisplayLayout.BOTTOM_RIGHT_2x2;
                } else {
                    layout = DisplayLayout.BOTTOM_LEFT_2x2;
                }
                return new LayoutResult(layout, square);
            }
        }

        // Fallback to 2x1 / 1x2 / single (unchanged logic)
        boolean hasLeft = positions.contains(origin.subtract(left));
        boolean hasRight = positions.contains(origin.offset(left));
        boolean hasUp = positions.contains(origin.below());
        boolean hasDown = positions.contains(origin.above());

        if (hasLeft) return new LayoutResult(DisplayLayout.LEFT_2x1, Set.of(origin, origin.subtract(left)));
        if (hasRight) return new LayoutResult(DisplayLayout.RIGHT_2x1, Set.of(origin, origin.offset(left)));
        if (hasUp) return new LayoutResult(DisplayLayout.TOP_1x2, Set.of(origin, origin.below()));
        if (hasDown) return new LayoutResult(DisplayLayout.BOTTOM_1x2, Set.of(origin, origin.above()));

        return new LayoutResult(DisplayLayout.SINGLE_1x1, Set.of(origin));
    }

    private record LayoutResult(DisplayLayout layout, Set<BlockPos> blocks){}


}
