package g_mungus.zps.entity;

import net.neoforged.neoforge.items.IItemHandler;

/**
 * Implemented by non-living entities that react to being on a sift's mesh. Living entities are
 * skipped outright, so implementing this on one has no effect.
 *
 * <p>{@link g_mungus.zps.block.SiftBlock} calls {@link #sift} once per tick, server-side only, for
 * as long as the entity overlaps the mesh with its centre below the middle of the block. The
 * handler passed in is the sift's own five-slot inventory, so an implementation may both read and
 * modify it.
 */
public interface Siftable {
    /**
     * Called while this entity rests on a sift's mesh.
     *
     * @param inventory the sift's inventory, free to be read from and written to
     */
    void sift(IItemHandler inventory);
}
