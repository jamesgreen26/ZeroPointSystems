package g_mungus.zps.block;

import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class IncompleteRoboticArmBlock extends Block {
    private final int segmentCount;

    public IncompleteRoboticArmBlock(Properties properties, int segmentCount) {
        super(properties);
        this.segmentCount = segmentCount;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return RoboticArmBlock.SHAPE;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hitResult) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!heldStack.is(ModItems.ROBOTIC_ARM_SEGMENT.get())) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            Block nextBlock = switch (segmentCount) {
                case 0 -> ModBlocks.INCOMPLETE_ROBOTIC_ARM_1.get();
                case 1 -> ModBlocks.INCOMPLETE_ROBOTIC_ARM_2.get();
                default -> ModBlocks.ROBOTIC_ARM.get();
            };

            level.setBlock(pos, nextBlock.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 0.7f, 1.2f);

            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
