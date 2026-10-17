package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.LargeSpaceDoorBlockEntity;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * A 3x3 sliding door, after Ad Astra's sliding doors (MIT). Nine blocks share the state; the bottom middle one is
 * the controller, whose block entity animates and renders the two halves sliding apart. Opens by hand or while
 * powered; sneak-using with an empty hand or a wrench locks it so only redstone opens it.
 */
public class LargeSpaceDoorBlock extends BaseEntityBlock {
    public static final MapCodec<LargeSpaceDoorBlock> CODEC = simpleCodec(LargeSpaceDoorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    // One pixel in from the side the door faces.
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 1, 16, 16, 4);
    private static final VoxelShape EAST_SHAPE = Block.box(12, 0, 0, 15, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 12, 16, 16, 15);
    private static final VoxelShape WEST_SHAPE = Block.box(1, 0, 0, 4, 16, 16);

    /** How far each half slides when fully open, in blocks: the same as Ad Astra's sliding doors. */
    public static final float OPEN_TRAVEL = 100 / 81f;
    /** Per facing, then per part: the closed slab cut down to what the slid-open halves still cover. */
    private static final Map<Direction, Map<Part, VoxelShape>> OPEN_SHAPES = openShapes();

    public LargeSpaceDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(LOCKED, false)
                .setValue(POWERED, false)
                .setValue(PART, Part.BOTTOM));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LOCKED, POWERED, PART);
    }

    /** Open by hand or by redstone. */
    public static boolean isOpen(BlockState state) {
        return state.getValue(OPEN) || state.getValue(POWERED);
    }

    /** The direction from the door's left column to its right one, as seen from the side it faces. */
    private static Direction across(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos controller, BlockState state, Part part) {
        return controller.relative(across(state), part.x).above(part.y);
    }

    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.relative(across(state), -part.x).below(part.y);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                                           @NotNull CollisionContext context) {
        if (isOpen(state)) {
            return OPEN_SHAPES.get(state.getValue(FACING)).get(state.getValue(PART));
        }
        return closedShape(state.getValue(FACING));
    }

    private static VoxelShape closedShape(Direction facing) {
        return switch (facing) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    /**
     * Where the open halves are drawn, part by part. In door pixels (x 0..48 from the viewer's left, y 0..48 up),
     * texture row r spans y 47-r..48-r and splits at the seam: the left half is x below {@link #seamEdge}, the right
     * half the rest. Open, each half sits {@link #OPEN_TRAVEL} further out to its side.
     */
    private static Map<Direction, Map<Part, VoxelShape>> openShapes() {
        double travel = OPEN_TRAVEL * 16;
        Map<Direction, Map<Part, VoxelShape>> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<Part, VoxelShape> byPart = new EnumMap<>(Part.class);
            for (Part part : Part.values()) {
                double cellX = (part.x + 1) * 16;
                double cellY = part.y * 16;
                VoxelShape shape = Shapes.empty();
                for (int row = 0; row < 48; row++) {
                    double y0 = Math.max(47 - row - cellY, 0);
                    double y1 = Math.min(48 - row - cellY, 16);
                    if (y0 >= y1) {
                        continue;
                    }
                    int edge = seamEdge(row);
                    shape = Shapes.or(shape,
                            slab(facing, -travel - cellX, edge - travel - cellX, y0, y1),
                            slab(facing, edge + travel - cellX, 48 + travel - cellX, y0, y1));
                }
                byPart.put(part, Shapes.join(shape, closedShape(facing), BooleanOp.AND).optimize());
            }
            shapes.put(facing, byPart);
        }
        return shapes;
    }

    /** Where the left half ends in a texture row: the seam runs down at x 17, steps one pixel a row, then x 30. */
    private static int seamEdge(int row) {
        return Math.clamp(row, 17, 30) + 1;
    }

    /** A box across one block of the door, u0..u1 pixels from the viewer's left, clipped to the block. */
    private static VoxelShape slab(Direction facing, double u0, double u1, double y0, double y1) {
        u0 = Math.max(u0, 0);
        u1 = Math.min(u1, 16);
        if (u0 >= u1) {
            return Shapes.empty();
        }
        return switch (facing) {
            case SOUTH -> Block.box(u0, y0, 0, u1, y1, 16);
            case EAST -> Block.box(0, y0, 16 - u1, 16, y1, 16 - u0);
            case WEST -> Block.box(0, y0, u0, 16, y1, u1);
            default -> Block.box(16 - u1, y0, 0, 16 - u0, y1, 16);
        };
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                    @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return isOpen(state) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType type) {
        return type != PathComputationType.WATER && isOpen(state);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // the controller's renderer draws the whole door
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Level level = context.getLevel();
        BlockPos controller = context.getClickedPos();
        if (!level.getBlockState(controller.below()).isFaceSturdy(level, controller.below(), Direction.UP)) {
            return null;
        }
        boolean powered = false;
        for (Part part : Part.values()) {
            BlockPos pos = partPos(controller, state, part);
            if (pos.getY() > level.getMaxBuildHeight() - 1 || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
            powered |= level.hasNeighborSignal(pos);
        }
        return state.setValue(POWERED, powered);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, @NotNull ItemStack stack) {
        for (Part part : Part.values()) {
            if (part != Part.BOTTOM) {
                level.setBlock(partPos(pos, state, part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
                                                       @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown() && Compat.isCreateWrench(stack.getItem())) {
            toggleLock(state, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            toggleLock(state, level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (state.getValue(LOCKED)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            setAll(level, pos, state, OPEN, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void toggleLock(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return;
        }
        boolean locked = !state.getValue(LOCKED);
        setAll(level, pos, state, LOCKED, locked);
        player.displayClientMessage(Component.translatable(locked
                ? "message.zps.large_space_door.locked" : "message.zps.large_space_door.unlocked"), true);
    }

    /** Sets one property on all nine parts, so each keeps the door's whole state. */
    private void setAll(Level level, BlockPos pos, BlockState state, BooleanProperty property, boolean value) {
        BlockPos controller = controllerPos(pos, state);
        for (Part part : Part.values()) {
            BlockPos partPos = partPos(controller, state, part);
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(this) && partState.getValue(property) != value) {
                level.setBlock(partPos, partState.setValue(property, value), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                   @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        BlockPos controller = controllerPos(pos, state);
        boolean powered = false;
        for (Part part : Part.values()) {
            powered |= level.hasNeighborSignal(partPos(controller, state, part));
        }
        setAll(level, pos, state, POWERED, powered);
    }

    /** A part whose neighbour within the door is gone breaks too, so the door always goes as a whole. */
    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                              @NotNull BlockState neighborState, @NotNull LevelAccessor level,
                                              @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Direction across = across(state);
        int dx = direction == across ? 1 : direction == across.getOpposite() ? -1 : 0;
        int dy = direction == Direction.UP ? 1 : direction == Direction.DOWN ? -1 : 0;
        boolean inDoor = (dx != 0 || dy != 0) && Math.abs(part.x + dx) <= 1 && part.y + dy >= 0 && part.y + dy <= 2;
        if (inDoor && !(neighborState.is(this) && neighborState.getValue(FACING) == state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** Only the controller drops the door. Broken elsewhere in creative, take it out first so it drops nothing. */
    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                                                 @NotNull Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(PART) != Part.BOTTOM) {
            BlockPos controller = controllerPos(pos, state);
            if (level.getBlockState(controller).is(this)) {
                level.setBlock(controller, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return state.getValue(PART) == Part.BOTTOM ? new LargeSpaceDoorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                            @NotNull BlockState state,
                                                                            @NotNull BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LARGE_SPACE_DOOR.get(), LargeSpaceDoorBlockEntity::tick);
    }

    /** Each block's place in the door: columns -1..1 left to right as seen from the front, rows 0..2 upwards. */
    public enum Part implements StringRepresentable {
        TOP_LEFT(-1, 2), TOP(0, 2), TOP_RIGHT(1, 2),
        LEFT(-1, 1), CENTER(0, 1), RIGHT(1, 1),
        BOTTOM_LEFT(-1, 0), BOTTOM(0, 0), BOTTOM_RIGHT(1, 0);

        public final int x;
        public final int y;

        Part(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
