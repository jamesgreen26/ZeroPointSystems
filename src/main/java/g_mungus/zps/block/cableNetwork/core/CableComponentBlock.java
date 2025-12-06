package g_mungus.zps.block.cableNetwork.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public abstract class CableComponentBlock extends Block implements CableNetworkComponent{
    public CableComponentBlock(Properties arg) {
        super(arg);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);

        updateSelfAndNeighbors(newState, level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(newState, level, pos, oldState, moved);

        updateSelfAndNeighbors(newState, level, pos, oldState);
    }
}
