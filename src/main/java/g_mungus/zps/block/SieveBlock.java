package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.SieveBlockEntity;
import g_mungus.zps.entity.Siftable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A sieve pan holding five stacks. No processing yet: the inventory is storage only.
 *
 * <p>Only the frame collides; the mesh in the middle is pass-through, so anything landing on the
 * sieve drops into the block below.
 *
 * <p>The pan is wider than its block: the frame rails hang four pixels past the cube on every side
 * and the mesh a further pixel. Sieves therefore connect to each other — each block draws only the
 * regions of the pan it owns, and drops the rail and mesh lip on any side that abuts another sieve,
 * because that neighbour's own centre already covers the same space. Adjacent sieves read as one
 * continuous pan with no doubled rails down the seam.
 *
 * <p>Where the array turns a concave corner — one side exposed, the side beside it connected, and a
 * sieve on the diagonal between them — two exposed runs meet and their rails would cross. The
 * {@code inner_*} properties mark those corners so the two runs can miter instead. Walking the
 * outline north west→east, east north→south, south east→west, west south→north, the run that starts
 * at such a corner claims the join and gives up a pixel so it stops on the inner corner line; the
 * run that ends there yields the whole four pixel band, and a pixel of its mesh lip.
 *
 * <p>Collision lookups only visit blocks intersecting the entity's own bounding box, so an entity
 * standing over a rail but clear of the sieve's own column can miss it.
 */
public class SieveBlock extends BaseEntityBlock {
    private static final MapCodec<SieveBlock> CODEC = simpleCodec(SieveBlock::new);

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    /** Set on a corner where exactly one of its two sides connects and the diagonal is a sieve. */
    public static final BooleanProperty INNER_NORTH_WEST = BooleanProperty.create("inner_nw");
    public static final BooleanProperty INNER_NORTH_EAST = BooleanProperty.create("inner_ne");
    public static final BooleanProperty INNER_SOUTH_EAST = BooleanProperty.create("inner_se");
    public static final BooleanProperty INNER_SOUTH_WEST = BooleanProperty.create("inner_sw");

    /** The mesh inside the block's own column — always drawn, whatever the neighbours are. */
    private static final VoxelShape CENTRE = Block.box(0.0, 7.0, 0.0, 16.0, 9.0, 16.0);

    /**
     * Rail plus mesh lip along one edge, dropped when that side abuts another sieve. A concave
     * corner at the run's start trims a pixel off the rail; one at its end takes the whole four
     * pixel band plus a pixel of mesh, leaving the perpendicular run to fill the join.
     */
    private static VoxelShape northEdge(boolean innerWest, boolean innerEast) {
        return Shapes.or(
                Block.box(innerWest ? 1.0 : 0.0, 4.0, -4.0, innerEast ? 12.0 : 16.0, 12.0, -1.0),
                Block.box(0.0, 7.0, -1.0, innerEast ? 15.0 : 16.0, 9.0, 0.0));
    }

    private static VoxelShape eastEdge(boolean innerNorth, boolean innerSouth) {
        return Shapes.or(
                Block.box(17.0, 4.0, innerNorth ? 1.0 : 0.0, 20.0, 12.0, innerSouth ? 12.0 : 16.0),
                Block.box(16.0, 7.0, 0.0, 17.0, 9.0, innerSouth ? 15.0 : 16.0));
    }

    private static VoxelShape southEdge(boolean innerWest, boolean innerEast) {
        return Shapes.or(
                Block.box(innerWest ? 4.0 : 0.0, 4.0, 17.0, innerEast ? 15.0 : 16.0, 12.0, 20.0),
                Block.box(innerWest ? 1.0 : 0.0, 7.0, 16.0, 16.0, 9.0, 17.0));
    }

    private static VoxelShape westEdge(boolean innerNorth, boolean innerSouth) {
        return Shapes.or(
                Block.box(-4.0, 4.0, innerNorth ? 4.0 : 0.0, -1.0, 12.0, innerSouth ? 15.0 : 16.0),
                Block.box(-1.0, 7.0, innerNorth ? 1.0 : 0.0, 0.0, 9.0, 16.0));
    }

    /** Where two rails meet, dropped as soon as either of its two sides connects. */
    private static final VoxelShape CORNER_NORTH_WEST = Shapes.or(
            Block.box(-1.0, 4.0, -4.0, 0.0, 12.0, -1.0),
            Block.box(-4.0, 4.0, -4.0, -1.0, 12.0, 0.0),
            Block.box(-1.0, 7.0, -1.0, 0.0, 9.0, 0.0));
    private static final VoxelShape CORNER_NORTH_EAST = Shapes.or(
            Block.box(16.0, 4.0, -4.0, 20.0, 12.0, -1.0),
            Block.box(17.0, 4.0, -1.0, 20.0, 12.0, 0.0),
            Block.box(16.0, 7.0, -1.0, 17.0, 9.0, 0.0));
    private static final VoxelShape CORNER_SOUTH_EAST = Shapes.or(
            Block.box(17.0, 4.0, 16.0, 20.0, 12.0, 20.0),
            Block.box(16.0, 4.0, 17.0, 17.0, 12.0, 20.0),
            Block.box(16.0, 7.0, 16.0, 17.0, 9.0, 17.0));
    private static final VoxelShape CORNER_SOUTH_WEST = Shapes.or(
            Block.box(-4.0, 4.0, 16.0, -1.0, 12.0, 17.0),
            Block.box(-4.0, 4.0, 17.0, 0.0, 12.0, 20.0),
            Block.box(-1.0, 7.0, 16.0, 0.0, 9.0, 17.0));

    private static final int NORTH_BIT = 1;
    private static final int EAST_BIT = 2;
    private static final int SOUTH_BIT = 4;
    private static final int WEST_BIT = 8;
    private static final int INNER_NORTH_WEST_BIT = 16;
    private static final int INNER_NORTH_EAST_BIT = 32;
    private static final int INNER_SOUTH_EAST_BIT = 64;
    private static final int INNER_SOUTH_WEST_BIT = 128;

    /** Outline per connection mask, matching the multipart model region for region. */
    private static final VoxelShape[] OUTLINES = new VoxelShape[256];

    static {
        for (int mask = 0; mask < OUTLINES.length; mask++) {
            boolean north = (mask & NORTH_BIT) != 0;
            boolean east = (mask & EAST_BIT) != 0;
            boolean south = (mask & SOUTH_BIT) != 0;
            boolean west = (mask & WEST_BIT) != 0;
            boolean innerNorthWest = (mask & INNER_NORTH_WEST_BIT) != 0;
            boolean innerNorthEast = (mask & INNER_NORTH_EAST_BIT) != 0;
            boolean innerSouthEast = (mask & INNER_SOUTH_EAST_BIT) != 0;
            boolean innerSouthWest = (mask & INNER_SOUTH_WEST_BIT) != 0;

            VoxelShape shape = CENTRE;
            if (!north) shape = Shapes.or(shape, northEdge(innerNorthWest, innerNorthEast));
            if (!east) shape = Shapes.or(shape, eastEdge(innerNorthEast, innerSouthEast));
            if (!south) shape = Shapes.or(shape, southEdge(innerSouthWest, innerSouthEast));
            if (!west) shape = Shapes.or(shape, westEdge(innerNorthWest, innerSouthWest));
            if (!north && !west) shape = Shapes.or(shape, CORNER_NORTH_WEST);
            if (!north && !east) shape = Shapes.or(shape, CORNER_NORTH_EAST);
            if (!south && !east) shape = Shapes.or(shape, CORNER_SOUTH_EAST);
            if (!south && !west) shape = Shapes.or(shape, CORNER_SOUTH_WEST);

            OUTLINES[mask] = shape.optimize();
        }
    }

    /** The sieve plate itself, the part an entity has to touch for the sieve to act on it. */
    private static final VoxelShape MESH = Block.box(-1.0, 7.0, -1.0, 17.0, 9.0, 17.0);

    private static final AABB MESH_BOUNDS = MESH.bounds();

    /** Half the block's height: an entity only sieves once its centre has sunk past this. */
    private static final double BLOCK_MID_Y = 0.5;

    private static final VoxelShape OUTLINE_CONTAINED =
            Shapes.join(OUTLINES[0], Shapes.block(), BooleanOp.AND);

    public SieveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(INNER_NORTH_WEST, false)
                .setValue(INNER_NORTH_EAST, false)
                .setValue(INNER_SOUTH_EAST, false)
                .setValue(INNER_SOUTH_WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST,
                INNER_NORTH_WEST, INNER_NORTH_EAST, INNER_SOUTH_EAST, INNER_SOUTH_WEST);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.SIEVE.get().create(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return withConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                           @NotNull BlockState neighborState, @NotNull LevelAccessor level,
                                           @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return withConnections(state, level, pos);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * Reads the eight surrounding columns and fills in every connection property.
     *
     * <p>A corner is inner — the array turning back on itself — when exactly one of its two sides
     * connects and there is a sieve on the diagonal between them. With both sides connected the
     * corner is interior and neither edge is drawn; with neither, it is an ordinary outside corner.
     */
    private BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        boolean north = connectsTo(level.getBlockState(pos.north()));
        boolean east = connectsTo(level.getBlockState(pos.east()));
        boolean south = connectsTo(level.getBlockState(pos.south()));
        boolean west = connectsTo(level.getBlockState(pos.west()));

        return state
                .setValue(NORTH, north)
                .setValue(EAST, east)
                .setValue(SOUTH, south)
                .setValue(WEST, west)
                .setValue(INNER_NORTH_WEST, north != west && connectsTo(level.getBlockState(pos.north().west())))
                .setValue(INNER_NORTH_EAST, north != east && connectsTo(level.getBlockState(pos.north().east())))
                .setValue(INNER_SOUTH_EAST, south != east && connectsTo(level.getBlockState(pos.south().east())))
                .setValue(INNER_SOUTH_WEST, south != west && connectsTo(level.getBlockState(pos.south().west())));
    }

    /** Sieves only ever join up with other sieves. */
    private boolean connectsTo(BlockState neighborState) {
        return neighborState.is(this);
    }

    /**
     * Vanilla only reshapes the four blocks sharing a face, but the inner corner properties also
     * read the diagonals, so those have to be refreshed by hand whenever a sieve appears or goes.
     * The connections depend on nothing but which columns hold sieves, so a client-only update is
     * enough and cannot cascade.
     */
    private void refreshDiagonals(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos diagonal = pos.relative(direction).relative(direction.getClockWise());
            BlockState diagonalState = level.getBlockState(diagonal);
            if (!diagonalState.is(this)) {
                continue;
            }
            BlockState updated = withConnections(diagonalState, level, diagonal);
            if (updated != diagonalState) {
                level.setBlock(diagonal, updated, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int connectionMask(BlockState state) {
        int mask = 0;
        if (state.getValue(NORTH)) mask |= NORTH_BIT;
        if (state.getValue(EAST)) mask |= EAST_BIT;
        if (state.getValue(SOUTH)) mask |= SOUTH_BIT;
        if (state.getValue(WEST)) mask |= WEST_BIT;
        if (state.getValue(INNER_NORTH_WEST)) mask |= INNER_NORTH_WEST_BIT;
        if (state.getValue(INNER_NORTH_EAST)) mask |= INNER_NORTH_EAST_BIT;
        if (state.getValue(INNER_SOUTH_EAST)) mask |= INNER_SOUTH_EAST_BIT;
        if (state.getValue(INNER_SOUTH_WEST)) mask |= INNER_SOUTH_WEST_BIT;
        return mask;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SieveBlockEntity sieve && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(sieve, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                        @NotNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            refreshDiagonals(level, pos);
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SieveBlockEntity sieve) {
                sieve.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            refreshDiagonals(level, pos);
        }
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return OUTLINES[connectionMask(state)];
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (context instanceof EntityCollisionContext entityCollisionContext
                && entityCollisionContext.getEntity() instanceof LivingEntity) {
            return OUTLINE_CONTAINED;
        }

        return Shapes.empty();
    }

    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, Entity entity) {
        if (!entity.getBoundingBox().intersects(MESH_BOUNDS.move(pos)) || entity instanceof LivingEntity) {
            return;
        }

        if (entity.getBoundingBox().getCenter().y < pos.getY() + BLOCK_MID_Y
                && entity instanceof Siftable siftable && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof SieveBlockEntity sieve) {
            siftable.sift(sieve.getInventory());
        }

        entity.makeStuckInBlock(state, new Vec3(0.75, 0.75, 0.75));
    }
}
