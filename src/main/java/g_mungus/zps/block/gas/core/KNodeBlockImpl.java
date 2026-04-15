package g_mungus.zps.block.gas.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class KNodeBlockImpl extends Block implements KNodeBlock {

    public abstract @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos);


    public KNodeBlockImpl(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean moved
    ) {
        super.onPlace(state, level, pos, oldState, moved);
        nodePlace(state, level, pos, oldState, moved);

        BlockState newState = getConnectedState(level, state, pos);

        if (newState != null) {
            level.setBlockAndUpdate(pos, newState);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean moved
    ) {
        nodeRemove(state, level, pos, oldState, moved);
        super.onRemove(state, level, pos, oldState, moved);
    }
}
