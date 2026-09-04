package g_mungus.zps.block.reactor;

import g_mungus.zps.reactor.ReactorWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Reactor wall you can see through. Same stats as plating; only the rendering differs. */
public class ReinforcedGlassBlock extends TransparentBlock implements ReactorWallBlock {

    public ReinforcedGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                           @NotNull BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            ReactorWallBlock.onPlaced(level, pos);
        }
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!newState.is(this)) {
            ReactorWallBlock.onRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
