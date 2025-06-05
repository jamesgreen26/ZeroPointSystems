package g_mungus.block.cableNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface QuadCableNetworkComponent extends QuadCableNetworkAble {
     enum Channel {
        GREEN, BLUE, RED, PURPLE;

        int getIndex() {
            return switch (this) {
                case GREEN -> 0;
                case BLUE -> 1;
                case RED -> 2;
                case PURPLE -> 3;
            };
        }
    }

}
