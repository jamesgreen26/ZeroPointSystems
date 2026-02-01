package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SwitchPanelBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty POWERED_0 = BooleanProperty.create("powered_0");
    public static final BooleanProperty POWERED_1 = BooleanProperty.create("powered_1");
    public static final BooleanProperty POWERED_2 = BooleanProperty.create("powered_2");
    public static final BooleanProperty POWERED_3 = BooleanProperty.create("powered_3");

    public static final VoxelShape NORTH_SHAPE;
    public static final VoxelShape EAST_SHAPE;
    public static final VoxelShape SOUTH_SHAPE;
    public static final VoxelShape WEST_SHAPE;
    public static final VoxelShape UP_SHAPE;
    public static final VoxelShape DOWN_SHAPE;

    public SwitchPanelBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(POWERED_0, false)
                .setValue(POWERED_1, false)
                .setValue(POWERED_2, false)
                .setValue(POWERED_3, false)
        );
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        arg.add(FACE, FACING, POWERED_0, POWERED_1, POWERED_2, POWERED_3);
    }

    public VoxelShape getShape(BlockState arg, BlockGetter arg2, BlockPos arg3, CollisionContext arg4) {
        return switch (arg.getValue(FACE)) {
            case FLOOR -> DOWN_SHAPE;
            case WALL -> switch (arg.getValue(FACING)) {
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                default -> NORTH_SHAPE;
            };
            default -> UP_SHAPE;
        };
    }

    static {
        NORTH_SHAPE = Block.box(0, 0, 8, 16, 16, 16);
        SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 8);
        WEST_SHAPE  = Block.box(8, 0, 0, 16, 16, 16);
        EAST_SHAPE  = Block.box(0, 0, 0, 8, 16, 16);
        UP_SHAPE    = Block.box(0, 8, 0, 16, 16, 16);
        DOWN_SHAPE  = Block.box(0, 0, 0, 16, 8, 16);
    }
}
