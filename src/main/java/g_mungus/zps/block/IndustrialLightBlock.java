package g_mungus.zps.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class IndustrialLightBlock extends RedstoneLampBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final Map<Direction, VoxelShape> shapes;

    public IndustrialLightBlock(Properties properties, double[][] upFacingBoxes) {
        super(properties);
        this.shapes = buildShapes(upFacingBoxes);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> buildShapes(double[][] upFacingBoxes) {
        return Maps.newEnumMap(ImmutableMap.of(
                Direction.UP, buildShapeForFacing(upFacingBoxes, Direction.UP),
                Direction.DOWN, buildShapeForFacing(upFacingBoxes, Direction.DOWN),
                Direction.NORTH, buildShapeForFacing(upFacingBoxes, Direction.NORTH),
                Direction.SOUTH, buildShapeForFacing(upFacingBoxes, Direction.SOUTH),
                Direction.EAST, buildShapeForFacing(upFacingBoxes, Direction.EAST),
                Direction.WEST, buildShapeForFacing(upFacingBoxes, Direction.WEST)
        ));
    }

    private static VoxelShape buildShapeForFacing(double[][] upFacingBoxes, Direction facing) {
        VoxelShape shape = Shapes.empty();

        for (double[] box : upFacingBoxes) {
            double[] rotated = rotateBox(box, facing);
            shape = Shapes.or(shape, Block.box(rotated[0], rotated[1], rotated[2], rotated[3], rotated[4], rotated[5]));
        }

        return shape;
    }

    private static double[] rotateBox(double[] box, Direction facing) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int xi = 0; xi <= 1; xi++) {
            for (int yi = 0; yi <= 1; yi++) {
                for (int zi = 0; zi <= 1; zi++) {
                    double x = xi == 0 ? box[0] : box[3];
                    double y = yi == 0 ? box[1] : box[4];
                    double z = zi == 0 ? box[2] : box[5];

                    double[] rotated = rotatePoint(x, y, z, facing);
                    minX = Math.min(minX, rotated[0]);
                    minY = Math.min(minY, rotated[1]);
                    minZ = Math.min(minZ, rotated[2]);
                    maxX = Math.max(maxX, rotated[0]);
                    maxY = Math.max(maxY, rotated[1]);
                    maxZ = Math.max(maxZ, rotated[2]);
                }
            }
        }

        return new double[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static double[] rotatePoint(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case UP -> new double[] { x, y, z };
            case DOWN -> new double[] { x, 16.0 - y, 16.0 - z };
            case NORTH -> new double[] { x, z, 16.0 - y };
            case SOUTH -> new double[] { x, 16.0 - z, y };
            case EAST -> new double[] { y, 16.0 - x, z };
            case WEST -> new double[] { 16.0 - y, x, z };
        };
    }
}
