package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.RollingMillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RollingMillBlock extends BaseEntityBlock {
    private static final MapCodec<RollingMillBlock> CODEC = simpleCodec(RollingMillBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    /** The mill's material openings: the top and bottom of the central roller slot. */
    private static final Direction[] OPENINGS = {Direction.UP, Direction.DOWN};

    public RollingMillBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.ROLLING_MILL.get().create(pos, state);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RollingMillBlockEntity rollingMill && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(rollingMill, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RollingMillBlockEntity rollingMill) {
                rollingMill.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, be) -> {
            if (be instanceof RollingMillBlockEntity rollingMill) {
                rollingMill.serverTick();
            }
        };
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!isWorking(state)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof RollingMillBlockEntity rollingMill)) {
            return;
        }
        ItemStack ingredient = rollingMill.getClientIngredient();
        if (ingredient.isEmpty()) {
            return;
        }
        // Crumbs of the ingredient's texture, so players can tell what's being rolled at a glance.
        ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, ingredient);
        boolean spreadAlongX = state.getValue(RollingMillBlock.FACING).getAxis() == Direction.Axis.Z;

        for (Direction opening : OPENINGS) {
            if (random.nextFloat() > 0.5f) {
                continue;
            }
            double along = (random.nextDouble() - 0.5) * 0.55;
            double across = (random.nextDouble() - 0.5) * 0.15;
            double x = pos.getX() + 0.5 + (spreadAlongX ? along : across);
            double z = pos.getZ() + 0.5 + (spreadAlongX ? across : along);
            boolean top = opening == Direction.UP;
            // Spawn beyond the face and fling further outward so the crumbs clear the block and read clearly.
            double y = pos.getY() + (top ? 1 : 0);
            double vy = top ? 0.15 : 0;

            level.addParticle(particle, x, y, z,
                    (random.nextDouble() - 0.5) * 0.1, vy, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    public boolean isWorking(BlockState state) {
        return state.hasProperty(RollingMillBlock.WORKING) && state.getValue(RollingMillBlock.WORKING);
    }
}
