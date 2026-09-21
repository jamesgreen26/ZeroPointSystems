package g_mungus.zps.compat;

import net.minecraft.world.phys.Vec3;

/**
 * One moving grid, a Valkyrien Skies ship or a Sable sublevel, as far as anything that works across grids needs
 * to know it: how to get in and out of its block space, and how it is moving.
 * <p>
 * Both mods keep a grid's blocks in the same level as everything else, in a region far from where they appear,
 * and both leave entities in the world. So a block position from {@link #toLocal} is an ordinary position in the
 * level: its block state can be read and set like any other.
 * <p>
 * A snapshot of the grid's logical pose when asked for, to be used within the tick and not kept.
 */
public interface GridSpace {

    /** A world position, in this grid's block space. */
    Vec3 toLocal(Vec3 world);

    /** A position in this grid's block space, in the world. */
    Vec3 toWorld(Vec3 local);

    /**
     * How fast the part of the grid that is at {@code world} is moving through the world, turning included, in
     * blocks per tick: the units an entity's own velocity is in.
     */
    Vec3 velocityAt(Vec3 world);

    /** True when {@code other} is this same grid. */
    boolean isSameGrid(GridSpace other);
}
