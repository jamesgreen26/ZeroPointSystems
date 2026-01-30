package g_mungus.zps.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CatwalkStairsBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> SHAPES;

    public CatwalkStairsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isCrouching()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState()
                .setValue(FACING, facing);
    }

    public VoxelShape getShape(BlockState arg, BlockGetter arg2, BlockPos arg3, CollisionContext arg4) {
        return SHAPES.get(arg.getValue(FACING));
    }

    static {
        SHAPES = Maps.newEnumMap(ImmutableMap.of(
                Direction.NORTH, Shapes.or(Block.box(0.0F, 4.0F, 0.0F, 16.0F, 8.0F, 8.0F),Block.box(0.0F, 12.0F, 8.0F, 16.0F, 16.0F, 16.0F)),
                Direction.SOUTH, Shapes.or(Block.box(0.0F, 12.0F, 0.0F, 16.0F, 16.0F, 8.0F),Block.box(0.0F, 4.0F, 8.0F, 16.0F, 8.0F, 16.0F)),
                Direction.WEST, Shapes.or(Block.box(0.0F, 4.0F, 0.0F, 8.0F, 8.0F, 16.0F),Block.box(8.0F, 12.0F, 0.0F, 16.0F, 16.0F, 16.0F)),
                Direction.EAST, Shapes.or(Block.box(0.0F, 12.0F, 0.0F, 8.0F, 16.0F, 16.0F),Block.box(8.0F, 4.0F, 0.0F, 16.0F, 8.0F, 16.0F))
        ));
    }


}
