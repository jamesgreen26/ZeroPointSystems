package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

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

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = this.defaultBlockState();

        Direction clickedFace = ctx.getClickedFace();
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();

        if (clickedFace == Direction.UP) {
            // Placed on top of a block → panel faces up
            return state
                    .setValue(FACE, AttachFace.FLOOR)
                    .setValue(FACING, horizontalFacing);
        }

        if (clickedFace == Direction.DOWN) {
            // Placed under a block → panel faces down
            return state
                    .setValue(FACE, AttachFace.CEILING)
                    .setValue(FACING, horizontalFacing);
        }

        // Otherwise it's a wall placement
        return state
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, clickedFace);
    }


    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!isFrontFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // World → block-local (0..1)
        Vec3 hitPos = hit.getLocation();
        double x = hitPos.x - pos.getX();
        double y = hitPos.y - pos.getY();
        double z = hitPos.z - pos.getZ();

        int quadrant = getQuadrant(state, x, y, z);

        if (quadrant == -1) {
            return InteractionResult.PASS;
        }

        BooleanProperty prop = switch (quadrant) {
            case 0 -> POWERED_0;
            case 1 -> POWERED_1;
            case 2 -> POWERED_2;
            case 3 -> POWERED_3;
            default -> throw new IllegalStateException();
        };

        level.setBlock(
                pos,
                state.cycle(prop),
                Block.UPDATE_ALL
        );

        level.playSound(
                null,
                pos,
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                state.getValue(prop) ? 0.5F : 0.6F
        );

        level.updateNeighborsAt(pos, this);

        return InteractionResult.CONSUME;
    }

    private static boolean isFrontFace(BlockState state, Direction clicked) {
        AttachFace face = state.getValue(FACE);

        return switch (face) {
            case WALL     -> clicked == state.getValue(FACING);
            case FLOOR    -> clicked == Direction.UP;
            case CEILING  -> clicked == Direction.DOWN;
        };
    }


    private static int getQuadrant(BlockState state, double x, double y, double z) {
        double u, v;

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        switch (face) {
            case WALL -> {
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = y; }
                    case SOUTH -> { u = x;     v = y; }
                    case WEST  -> { u = z;     v = y; }
                    case EAST  -> { u = 1 - z; v = y; }
                    default -> { return -1; }
                }
            }
            case FLOOR -> {
                // Front face is horizontal, facing *facing*
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = z;     }
                    case SOUTH -> { u = x;     v = 1 - z; }
                    case WEST  -> { u = z;     v = x;     }
                    case EAST  -> { u = 1 - z; v = 1 - x; }
                    default -> { return -1; }
                }
            }

            case CEILING -> {
                // Same as FLOOR but vertically mirrored
                switch (facing) {
                    case NORTH -> { u = 1 - x; v = 1 - z; }
                    case SOUTH -> { u = x;     v = z;     }
                    case WEST  -> { u = z;     v = 1 - x; }
                    case EAST  -> { u = 1 - z; v = x;     }
                    default -> { return -1; }
                }
            }
            default -> { return -1; }
        }

        // Clamp just in case
        u = Mth.clamp(u, 0.0, 1.0);
        v = Mth.clamp(v, 0.0, 1.0);

        // Quadrants:
        // 0 | 1
        // -----
        // 2 | 3
        if (v > 0.5) {
            return u < 0.5 ? 0 : 1;
        } else {
            return u < 0.5 ? 2 : 3;
        }
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
