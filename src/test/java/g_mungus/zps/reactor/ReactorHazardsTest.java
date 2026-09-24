package g_mungus.zps.reactor;

import g_mungus.zps.compat.GridSpace;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether an entity's box counts as inside a cavity: in the world, where the cells line up with
 * the box, and on a tilted grid, where the box the cavity sweeps in the world is bigger than the
 * cavity and only the corners and centre brought back into the grid can tell the difference.
 */
public class ReactorHazardsTest {

    /** Interior cells 1..3 on every axis, so the cavity is the box 1..4 and the shell 0..5. */
    private static final Reactor CUBE = cube(new BlockPos(1, 1, 1), new BlockPos(3, 3, 3));
    private static final Vec3 CENTRE = new Vec3(2.5, 2.5, 2.5);
    private static final double PIG = 0.9;

    private static Reactor cube(BlockPos from, BlockPos to) {
        LongSet interior = new LongOpenHashSet();
        LongSet walls = new LongOpenHashSet();
        for (int x = from.getX() - 1; x <= to.getX() + 1; x++) {
            for (int y = from.getY() - 1; y <= to.getY() + 1; y++) {
                for (int z = from.getZ() - 1; z <= to.getZ() + 1; z++) {
                    boolean inside = x >= from.getX() && x <= to.getX()
                            && y >= from.getY() && y <= to.getY()
                            && z >= from.getZ() && z <= to.getZ();
                    (inside ? interior : walls).add(BlockPos.asLong(x, y, z));
                }
            }
        }
        return new Reactor(1, new CavityScan(interior, walls, from));
    }

    private static AABB boxAround(Vec3 centre, double size) {
        return AABB.ofSize(centre, size, size, size);
    }

    /** A grid turned about the cavity's centre and carried off to somewhere else in the world. */
    private record Tilted(Quaterniond rotation, Vec3 worldCentre) implements GridSpace {
        @Override
        public Vec3 toLocal(Vec3 world) {
            Vector3d v = new Vector3d(world.x - worldCentre.x, world.y - worldCentre.y, world.z - worldCentre.z);
            rotation.transformInverse(v);
            return new Vec3(v.x + CENTRE.x, v.y + CENTRE.y, v.z + CENTRE.z);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            Vector3d v = new Vector3d(local.x - CENTRE.x, local.y - CENTRE.y, local.z - CENTRE.z);
            rotation.transform(v);
            return new Vec3(v.x + worldCentre.x, v.y + worldCentre.y, v.z + worldCentre.z);
        }

        @Override
        public Vec3 velocityAt(Vec3 world) {
            return Vec3.ZERO;
        }

        @Override
        public boolean isSameGrid(GridSpace other) {
            return other == this;
        }
    }

    private static final Tilted TIPPED_30 = new Tilted(
            new Quaterniond().rotateX(Math.toRadians(30)), new Vec3(100.5, 64.5, -200.5));

    // --- in the world ---------------------------------------------------------------------------

    @Test
    void cavityBoundsSpanTheInteriorCells() {
        assertEquals(new AABB(1, 1, 1, 4, 4, 4), CUBE.bounds());
    }

    @Test
    void boxInTheMiddleIsInside() {
        assertTrue(ReactorHazards.touches(CUBE, null, boxAround(CENTRE, PIG)));
    }

    @Test
    void boxStandingOnTheFloorIsInside() {
        assertTrue(ReactorHazards.touches(CUBE, null, new AABB(2.05, 1.0, 2.05, 2.95, 1.9, 2.95)));
    }

    @Test
    void boxReachingInThroughAWallIsInside() {
        assertTrue(ReactorHazards.touches(CUBE, null, new AABB(3.5, 2.0, 2.0, 4.4, 2.9, 2.9)));
    }

    @Test
    void boxRestingAgainstTheOutsideOfAWallIsOutside() {
        // Its face is on the plane of the wall's outer face; the skin keeps it out.
        assertFalse(ReactorHazards.touches(CUBE, null, new AABB(5.0, 2.0, 2.0, 5.9, 2.9, 2.9)));
        // Embedded in the wall, up to the cavity's own face.
        assertFalse(ReactorHazards.touches(CUBE, null, new AABB(4.0, 2.0, 2.0, 4.9, 2.9, 2.9)));
    }

    @Test
    void boxElsewhereIsOutside() {
        assertFalse(ReactorHazards.touches(CUBE, null, boxAround(new Vec3(20, 2.5, 2.5), PIG)));
    }

    // --- on a tilted grid -----------------------------------------------------------------------

    @Test
    void worldBoxEnclosesTheTiltedCavity() {
        AABB world = ReactorHazards.enclose(TIPPED_30, CUBE.bounds());
        // Tipped 30 degrees about X: the cavity's half-width of 1.5 sweeps 1.5 (cos 30 + sin 30) in Y and Z.
        double swept = 1.5 * (Math.cos(Math.toRadians(30)) + Math.sin(Math.toRadians(30)));
        Vec3 c = TIPPED_30.worldCentre();
        assertEquals(c.x - 1.5, world.minX, 1e-9);
        assertEquals(c.x + 1.5, world.maxX, 1e-9);
        assertEquals(c.y - swept, world.minY, 1e-9);
        assertEquals(c.y + swept, world.maxY, 1e-9);
        assertEquals(c.z - swept, world.minZ, 1e-9);
        assertEquals(c.z + swept, world.maxZ, 1e-9);
    }

    @Test
    void boxInTheMiddleOfTheTiltedCavityIsInside() {
        assertTrue(ReactorHazards.touches(CUBE, TIPPED_30, boxAround(TIPPED_30.worldCentre(), PIG)));
    }

    @Test
    void boxInTheCornerTheTiltedCavitySweepsIsOutside() {
        // Up and out along the diagonal of the world box: inside that box, but past the shell's outer face
        // in the grid, where the cavity's corner does not reach.
        AABB box = boxAround(TIPPED_30.worldCentre().add(0, 2.4, 2.4), PIG);
        assertTrue(box.intersects(ReactorHazards.enclose(TIPPED_30, CUBE.bounds())),
                "the box should be in the swept world box for the test to mean anything");
        assertFalse(ReactorHazards.touches(CUBE, TIPPED_30, box));
    }

    @Test
    void boxInTheWallOfTheTiltedCavityIsOutside() {
        // Straight up in the grid's own frame: two blocks above the centre is the middle of the roof.
        Vec3 roof = TIPPED_30.toWorld(CENTRE.add(0, 2.0, 0));
        assertFalse(ReactorHazards.touches(CUBE, TIPPED_30, boxAround(roof, 0.5)));
        // One block above the centre is still cavity.
        Vec3 belowRoof = TIPPED_30.toWorld(CENTRE.add(0, 1.0, 0));
        assertTrue(ReactorHazards.touches(CUBE, TIPPED_30, boxAround(belowRoof, 0.5)));
    }
}
