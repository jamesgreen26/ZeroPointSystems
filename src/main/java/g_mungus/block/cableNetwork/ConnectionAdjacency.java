package g_mungus.block.cableNetwork;

import net.minecraft.core.BlockPos;

public record ConnectionAdjacency(BlockPos block, BlockPos connectedFrom, int channelForController) {
    BlockPos getFirst() {
        return block;
    }

    BlockPos getSecond() {
        return connectedFrom;
    }
}
