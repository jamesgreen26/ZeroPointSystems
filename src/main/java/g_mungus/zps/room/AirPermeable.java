package g_mungus.zps.room;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that lets air through in some of its states, whatever its collision shape — one a tag cannot say
 * for, since a tag covers every state of a block alike. Blocks that always let air through go in
 * {@link RoomScanner#AIR_PERMEABLE} instead.
 */
public interface AirPermeable {

    boolean letsAirThrough(BlockState state);
}
