package g_mungus.block.cableNetwork;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface CanConnectCables {
    boolean shouldCablesConnectToThis(BlockState blockState, Direction direction);
}
