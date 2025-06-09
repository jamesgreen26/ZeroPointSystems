package g_mungus.zps.util;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static List<BlockPos> getNeighbors(BlockPos origin) {
        return new ArrayList<>(List.of(
                origin.above(),
                origin.below(),
                origin.north(),
                origin.east(),
                origin.south(),
                origin.west()
        ));
    }
}
