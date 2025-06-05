package g_mungus.block.cableNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface QuadCableNetworkConnector extends QuadCableNetworkAble {
    BlockPos getConnectedComponent(BlockPos self, BlockPos from, BlockState selfState, Level level, int recursion);
}
