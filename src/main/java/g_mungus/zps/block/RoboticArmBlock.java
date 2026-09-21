package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RoboticArmBlock extends BaseEntityBlock {
    private static final MapCodec<RoboticArmBlock> CODEC = simpleCodec(RoboticArmBlock::new);
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            Block.box(2.0D, 8.0D, 2.0D, 14.0D, 14.0D, 14.0D)
    );

    public RoboticArmBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RoboticArmBlockEntity(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                             @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, blockPos, blockState, blockEntity) -> {
            if (blockEntity instanceof RoboticArmBlockEntity roboticArm) {
                roboticArm.tickServer();
            }
        };
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
                                                       @NotNull BlockPos pos, @NotNull Player player,
                                                       @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RoboticArmBlockEntity roboticArm)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            // Empty hand: take whatever the arm is holding.
            if (hand != InteractionHand.MAIN_HAND || roboticArm.getHeldStack().isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                player.setItemInHand(hand, roboticArm.takeHeldStack());
                playTransferSound(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Holding something: give the arm as much of it as it can accept.
        if (roboticArm.getAcceptableCount(stack) <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int given = roboticArm.giveHeldItems(stack);
            stack.shrink(given);
            playTransferSound(level, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void playTransferSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2F, 1.0F);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RoboticArmBlockEntity roboticArm) {
                roboticArm.dropHeldStackAtHandPosition();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
