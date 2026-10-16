package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A ladder on brackets against the wall, like a vanilla ladder. The top ladder of a run, with no space ladder
 * above it, has its rails loop over into hand holds.
 */
public class SpaceMetalLadderBlock extends LadderBlock {

    /** No space ladder above: the hand holds are drawn. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    // The rungs and the brackets behind them, against the wall on the side the ladder faces away from.
    private static final VoxelShape NORTH_SHAPE = Block.box(2, 0, 14, 14, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(2, 0, 0, 14, 16, 2);
    private static final VoxelShape WEST_SHAPE = Block.box(14, 0, 2, 16, 16, 14);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 2, 2, 16, 14);

    public SpaceMetalLadderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOP);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                                           @NotNull CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        BlockPos above = context.getClickedPos().above();
        return state.setValue(TOP, !context.getLevel().getBlockState(above).is(this));
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                              @NotNull BlockState neighborState, @NotNull LevelAccessor level,
                                              @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (direction == Direction.UP && updated.is(this)) {
            return updated.setValue(TOP, !neighborState.is(this));
        }
        return updated;
    }
}
