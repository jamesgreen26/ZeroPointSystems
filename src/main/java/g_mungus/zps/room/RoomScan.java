package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A sealed room found by {@link RoomScanner}.
 *
 * @param interior every cell air can reach, partial blocks inside the room included
 * @param boundary every cell outside the interior whose shape stopped air getting further
 */
public record RoomScan(LongSet interior, LongSet boundary) {

    public int volume() {
        return interior.size();
    }
}
