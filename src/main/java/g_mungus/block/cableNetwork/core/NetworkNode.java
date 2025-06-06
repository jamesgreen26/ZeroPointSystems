package g_mungus.block.cableNetwork.core;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record NetworkNode(BlockPos pos, int channel, boolean terminal) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkNode that)) return false;
        return channel() == that.channel() && terminal() == that.terminal() && Objects.equals(pos(), that.pos());
    }
}
