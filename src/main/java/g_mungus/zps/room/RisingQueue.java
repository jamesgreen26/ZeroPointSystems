package g_mungus.zps.room;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.BlockPos;

/**
 * The order {@link RoomScanner} fills in: always the highest pocket waiting, first come first served
 * within a height — air filling a space the way gas rises through it.
 *
 * <p>A sealed room is filled completely whatever the order, so it costs the same. Open air is where
 * it pays: a fill that spreads evenly gives up only at the size limit, tens of thousands of cells
 * out, while one that rises reaches the top of the world, a hole, in a few hundred.
 *
 * <p>One first-in-first-out bucket per height rather than a heap: a cell only ever queues its
 * neighbours, a height either side of its own, so finding the highest waiting bucket is a short
 * step down, never a search.
 */
final class RisingQueue {

    /** Every height a {@link BlockPos} can hold, which packs Y into 12 bits. */
    private static final int HEIGHTS = 1 << 12;
    private static final int LOWEST = -(HEIGHTS / 2);

    /** One bucket per height, made when first needed; indexed by {@code y - LOWEST}. */
    private final LongArrayFIFOQueue[] cells = new LongArrayFIFOQueue[HEIGHTS];
    private final IntArrayFIFOQueue[] regions = new IntArrayFIFOQueue[HEIGHTS];
    private int size;
    /** At or above the highest bucket with anything waiting. */
    private int top = -1;
    private int region;

    void enqueue(long cell, int region) {
        int index = BlockPos.getY(cell) - LOWEST;
        LongArrayFIFOQueue bucket = cells[index];
        if (bucket == null) {
            bucket = cells[index] = new LongArrayFIFOQueue();
            regions[index] = new IntArrayFIFOQueue();
        }
        bucket.enqueue(cell);
        regions[index].enqueue(region);
        if (index > top) {
            top = index;
        }
        size++;
    }

    boolean isEmpty() {
        return size == 0;
    }

    /** Take the next entry, and return its cell; its pocket is then {@link #region()}. */
    long dequeue() {
        LongArrayFIFOQueue bucket = cells[top];
        while (bucket == null || bucket.isEmpty()) {
            bucket = cells[--top];
        }
        size--;
        region = regions[top].dequeueInt();
        return bucket.dequeueLong();
    }

    /** The pocket of the entry last taken. */
    int region() {
        return region;
    }
}
