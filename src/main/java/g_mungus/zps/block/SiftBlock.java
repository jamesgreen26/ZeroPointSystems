package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.SiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A sieve pan holding five stacks. No processing yet: the inventory is storage only.
 *
 * <p>Only the frame collides; the mesh in the middle is pass-through, so anything landing on the
 * sift drops into the block below.
 *
 * <p>The frame rails are taken straight from the model, which means they reach past the block cube
 * into the four neighbouring blocks. Collision lookups only visit blocks intersecting the entity's
 * own bounding box, so an entity standing over a rail but clear of the sift's own column can miss
 * it.
 */
public class SiftBlock extends BaseEntityBlock {
    private static final MapCodec<SiftBlock> CODEC = simpleCodec(SiftBlock::new);

    /** The four rails, straight out of the model — they overhang the block cube on every side. */
    private static final VoxelShape FRAME = Shapes.or(
            Block.box(-1.0, 4.0, -4.0, 20.0, 12.0, -1.0),
            Block.box(-4.0, 4.0, -4.0, -1.0, 12.0, 17.0),
            Block.box(-4.0, 4.0, 17.0, 17.0, 12.0, 20.0),
            Block.box(17.0, 4.0, -1.0, 20.0, 12.0, 20.0)
    );

    /** The sieve plate itself, the part an entity has to touch for the sift to act on it. */
    private static final VoxelShape MESH = Block.box(-1.0, 7.0, -1.0, 17.0, 9.0, 17.0);

    private static final AABB MESH_BOUNDS = MESH.bounds();

    /** Frame plus the mesh, so the middle of the sift still highlights and can be clicked. */
    private static final VoxelShape OUTLINE = Shapes.or(FRAME, MESH);

    public SiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.SIFT.get().create(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SiftBlockEntity sift && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(sift, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SiftBlockEntity sift) {
                sift.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (context instanceof EntityCollisionContext entityCollisionContext
                && entityCollisionContext.getEntity() instanceof LivingEntity) {
            return OUTLINE;
        }

        return FRAME;
    }

    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, Entity entity) {
        if (!entity.getBoundingBox().intersects(MESH_BOUNDS.move(pos)) || entity instanceof LivingEntity) {
            return;
        }

        entity.makeStuckInBlock(state, new Vec3(0.75, 0.75, 0.75));
    }
}
