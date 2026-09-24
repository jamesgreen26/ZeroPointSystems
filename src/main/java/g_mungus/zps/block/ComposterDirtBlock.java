package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Redstone;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A full composter whose contents an Impact Piston has packed down into dirt.
 *
 * <p>It only ever exists in the world: the {@code zps:impact} recipe for a full composter makes it,
 * and using it pops the dirt out and leaves an ordinary empty composter behind. There is
 * deliberately no item form, so breaking it or picking it gives the vanilla composter.
 *
 * <p>Like a vanilla composter holding bone meal, it offers its dirt to whatever pulls from below,
 * so a hopper underneath empties it just as a player's hand would.
 */
public class ComposterDirtBlock extends Block implements WorldlyContainerHolder {
    /** A vanilla composter filled to the brim: the walls, with the contents one pixel below the rim. */
    private static final VoxelShape SHAPE = Shapes.join(
            Shapes.block(), Block.box(2.0, 15.0, 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);

    public ComposterDirtBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @NotNull VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide) {
            popDirt(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Mirrors how a vanilla composter hands over its bone meal: the item hops out of the top. */
    private static void popDirt(Level level, BlockPos pos) {
        ItemEntity dirt = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5, new ItemStack(Items.DIRT));
        dirt.setDefaultPickUpDelay();
        level.addFreshEntity(dirt);
        empty(level, pos);
        level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /** With the dirt gone, by hand or by hopper, what is left is an ordinary empty composter. */
    private static void empty(LevelAccessor level, BlockPos pos) {
        level.setBlock(pos, Blocks.COMPOSTER.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public @NotNull WorldlyContainer getContainer(@NotNull BlockState state, @NotNull LevelAccessor level, @NotNull BlockPos pos) {
        return new OutputContainer(state, level, pos);
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    /** Full strength, where a vanilla composter tops out at 8: a clear "the dirt is ready" reading. */
    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return Redstone.SIGNAL_MAX;
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull net.minecraft.world.phys.HitResult target, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Player player) {
        return new ItemStack(Items.COMPOSTER);
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull PathComputationType type) {
        return false;
    }

    /**
     * The vanilla composter's output container with dirt in place of bone meal: one item, offered
     * through the bottom face only, and taking it empties the composter.
     */
    private static class OutputContainer extends SimpleContainer implements WorldlyContainer {
        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        private OutputContainer(BlockState state, LevelAccessor level, BlockPos pos) {
            super(new ItemStack(Items.DIRT));
            this.state = state;
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int @NotNull [] getSlotsForFace(@NotNull Direction side) {
            return side == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, @NotNull ItemStack stack, @Nullable Direction side) {
            return false;
        }

        // A handler wrapping this container may outlive the dirt (taken by hand, block broken), so the
        // block is checked on every offer rather than only when this container emptied it.
        @Override
        public boolean canTakeItemThroughFace(int slot, @NotNull ItemStack stack, @NotNull Direction side) {
            return !changed && level.getBlockState(pos) == state && side == Direction.DOWN && stack.is(Items.DIRT);
        }

        @Override
        public void setChanged() {
            // Only empty the block this container was made for; it may have been used or broken since.
            if (!changed && level.getBlockState(pos) == state) {
                empty(level, pos);
            }
            changed = true;
        }
    }
}
