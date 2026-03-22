package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class WaterExplodingBlock extends Block {
    public WaterExplodingBlock(Properties arg) {
        super(arg);
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockGetter blockgetter = ctx.getLevel();
        BlockPos blockpos = ctx.getClickedPos();
        BlockState blockstate = blockgetter.getBlockState(blockpos);
        if (shouldExplode(blockgetter, blockpos, blockstate)) {
            explode(ctx.getLevel(), blockpos);
            return Blocks.AIR.defaultBlockState();
        } else {
            return super.getStateForPlacement(ctx);
        }
    }

    private static boolean shouldExplode(BlockGetter arg, BlockPos arg2, BlockState arg3, FluidState fluidState) {
        return arg3.canBeHydrated(arg, arg2, fluidState, arg2) || touchesLiquid(arg, arg2, arg3);
    }

    private static boolean shouldExplode(BlockGetter getter, BlockPos pos, BlockState state) {
        return shouldExplode(getter, pos, state, getter.getFluidState(pos));
    }

    private static boolean canExplode(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean touchesLiquid(BlockGetter getter, BlockPos pos, BlockState state) {
        boolean flag = false;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for(Direction direction : Direction.values()) {
            BlockState blockstate = getter.getBlockState(blockpos$mutableblockpos);
            if (direction != Direction.DOWN || state.canBeHydrated(getter, pos, blockstate.getFluidState(), blockpos$mutableblockpos)) {
                blockpos$mutableblockpos.setWithOffset(pos, direction);
                blockstate = getter.getBlockState(blockpos$mutableblockpos);
                if (state.canBeHydrated(getter, pos, blockstate.getFluidState(), blockpos$mutableblockpos) && !blockstate.isFaceSturdy(getter, pos, direction.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static void explode(Level level, BlockPos center) {
        level.explode(null, center.getX(), center.getY(), center.getZ(), 8f, Level.ExplosionInteraction.BLOCK);
    }

    public BlockState updateShape(BlockState state, Direction dir, BlockState state2, LevelAccessor levelAccessor, BlockPos pos, BlockPos pos2) {
        if (touchesLiquid(levelAccessor, pos, state)) {
            if (levelAccessor instanceof Level level) {
                explode(level, pos);
            }
            return Blocks.AIR.defaultBlockState();
        } else {
            return super.updateShape(state, dir, state2, levelAccessor, pos, pos2);
        }
    }
}
