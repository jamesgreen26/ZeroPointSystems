package g_mungus.zps.block.cableNetwork.light_pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RadioAntenna extends Block {

    public static final BooleanProperty TOP = BooleanProperty.create("top");

    private static final VoxelShape BASE_SHAPE = Block.box(
            6.0, 0.0, 6.0,
            10.0, 16.0, 10.0
    );

    private static final VoxelShape TOP_PLATFORM = Block.box(
            0.0, 16.0, 3.0,
            16.0, 18.0, 13.0
    );

    private static final VoxelShape TOP_SHAPE = Shapes.or(BASE_SHAPE, TOP_PLATFORM);

    public RadioAntenna(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOP_SHAPE : BASE_SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        boolean hasSameAbove = context.getLevel()
                .getBlockState(above)
                .is(this);

        return this.defaultBlockState()
                .setValue(TOP, !hasSameAbove);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            net.minecraft.core.Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == net.minecraft.core.Direction.UP) {
            boolean hasSameAbove = neighborState.is(this);
            return state.setValue(TOP, !hasSameAbove);
        }

        return state;
    }
}
