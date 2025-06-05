package g_mungus.block.cableNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface QuadCableNetworkAble {
    boolean canQuadConnectTo(BlockPos self, BlockPos to, BlockState selfState);

}
