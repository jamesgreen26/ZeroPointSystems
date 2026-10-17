package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A sealed room found by {@link RoomScanner}.
 *
 * @param interior every cell air can reach, partial blocks inside the room included
 * @param boundary every cell outside the interior whose shape stopped air getting further
 * @param pockets  for interior cells only partly in the room — a closed door's, say, where the
 *                 room's air is on one side of the plate and not the other — which of the cell's
 *                 pockets of air are, one bit each ({@link CellRegions}). Cells wholly in the room
 *                 are not listed.
 */
public record RoomScan(LongSet interior, LongSet boundary, Long2LongMap pockets) {

    public RoomScan(LongSet interior, LongSet boundary) {
        this(interior, boundary, new Long2LongOpenHashMap());
    }

    public int volume() {
        return interior.size();
    }
}
