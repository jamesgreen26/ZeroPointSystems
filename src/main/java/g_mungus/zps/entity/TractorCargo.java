package g_mungus.zps.entity;

/**
 * Implemented by falling blocks, so that a Tractor Beam can carry one without it catching on things.
 *
 * <p>A falling block is a cube square to the world, and a beam's column is only a clear shaft of exactly that
 * size in the panel's own grid. Pulled along a column that runs at an angle through another grid, which is what
 * happens between a ship and the world, the cube clips the corners of every block beside its path and jams. So
 * while a beam has hold of it, a falling block does not collide at all: what stands in its way was the scan's
 * business, before it was ever pulled loose.
 *
 * <p>The beam says so again every tick, and the block takes collision back for itself a tick after the beam
 * stops saying it. Nothing has to tell it that the beam switched off, broke or unloaded, so a block can never be
 * left passing through the world for good.
 */
public interface TractorCargo {
    /** Called every tick that a Tractor Beam is carrying this entity, on both sides. */
    void zps$carriedByBeam();
}
