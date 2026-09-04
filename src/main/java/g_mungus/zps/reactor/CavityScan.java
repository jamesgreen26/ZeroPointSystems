package g_mungus.zps.reactor;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

/**
 * One sealed cavity found by {@link CavityScanner}: the air cells inside it and the wall blocks
 * that touch them.
 *
 * <p>Positions are packed longs ({@link BlockPos#asLong()}) so a large shell stays cheap to hold
 * and to look up. The host is the lexicographically smallest interior cell; it is always air, so a
 * Kelvin node placed there can never collide with a block's own node.
 */
public record CavityScan(LongSet interior, LongSet walls, BlockPos host) {

    /** Number of interior cells, which is the chamber's volume in cubic metres. */
    public int volume() {
        return interior.size();
    }

    public int wallCount() {
        return walls.size();
    }
}
