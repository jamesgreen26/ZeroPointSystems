package g_mungus.zps.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/**
 * Reaches the private stalactite geometry helpers so {@code PowderSnowDripstone} can walk a
 * stalactite exactly the way vanilla's fluid drip does.
 */
@Mixin(PointedDripstoneBlock.class)
public interface PointedDripstoneBlockInvoker {

    @Invoker("findTip")
    static BlockPos zps$findTip(BlockState state, LevelAccessor level, BlockPos pos, int maxIterations, boolean isTipMerge) {
        throw new AssertionError("Mixin invoker not applied");
    }

    /** Returns the block the stalactite hangs from, not the topmost dripstone. */
    @Invoker("findRootBlock")
    static Optional<BlockPos> zps$findRootBlock(Level level, BlockPos pos, BlockState state, int maxIterations) {
        throw new AssertionError("Mixin invoker not applied");
    }

    @Invoker("isStalactiteStartPos")
    static boolean zps$isStalactiteStartPos(BlockState state, LevelReader level, BlockPos pos) {
        throw new AssertionError("Mixin invoker not applied");
    }

    @Invoker("canDripThrough")
    static boolean zps$canDripThrough(BlockGetter level, BlockPos pos, BlockState state) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
