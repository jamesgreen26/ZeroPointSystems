package g_mungus.zps.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scanner on a map-backed grid: a solid shell seals, any gap or face not fully covered lets air
 * out, partial blocks inside are walked into, and the size limit holds.
 */
public class RoomScannerTest {

    private static final VoxelShape BOTTOM_SLAB = Shapes.box(0, 0, 0, 1, 0.5, 1);
    /** A thin plate across the cell's west face, like a closed door. */
    private static final VoxelShape WEST_PLATE = Shapes.box(0, 0, 0, 3 / 16.0, 1, 1);

    /** A world of empty air, bounded by holes beyond ±32 so an open fill ends. */
    private static class Grid implements Function<BlockPos, VoxelShape> {
        private final Map<BlockPos, VoxelShape> shapes = new HashMap<>();

        Grid set(BlockPos pos, VoxelShape shape) {
            shapes.put(pos.immutable(), shape);
            return this;
        }

        /** A hollow box of solid blocks whose interior spans {@code from} to {@code to} inclusive. */
        Grid shell(BlockPos from, BlockPos to) {
            for (int x = from.getX() - 1; x <= to.getX() + 1; x++) {
                for (int y = from.getY() - 1; y <= to.getY() + 1; y++) {
                    for (int z = from.getZ() - 1; z <= to.getZ() + 1; z++) {
                        boolean inside = x >= from.getX() && x <= to.getX()
                                && y >= from.getY() && y <= to.getY()
                                && z >= from.getZ() && z <= to.getZ();
                        if (!inside) {
                            set(new BlockPos(x, y, z), Shapes.block());
                        }
                    }
                }
            }
            return this;
        }

        @Override
        public VoxelShape apply(BlockPos pos) {
            if (Math.abs(pos.getX()) > 32 || Math.abs(pos.getY()) > 32 || Math.abs(pos.getZ()) > 32) {
                return null;
            }
            return shapes.getOrDefault(pos, Shapes.empty());
        }
    }

    private static final BlockPos CENTRE = new BlockPos(2, 2, 2);

    private static Grid cube3() {
        return new Grid().shell(new BlockPos(1, 1, 1), new BlockPos(3, 3, 3));
    }

    @Test
    void solidShellSeals() {
        RoomScan scan = RoomScanner.scan(CENTRE, cube3(), 4096);
        assertNotNull(scan);
        assertEquals(27, scan.volume());
        // Every face-adjacent shell block, and none of the edges or corners air never touches.
        assertEquals(6 * 9, scan.boundary().size());
    }

    @Test
    void missingBlockLetsAirOut() {
        Grid grid = cube3().set(new BlockPos(0, 2, 2), Shapes.empty());
        assertNull(RoomScanner.scan(CENTRE, grid, 4096));
    }

    @Test
    void slabInsideStillSeals() {
        Grid grid = cube3().set(new BlockPos(2, 1, 2), BOTTOM_SLAB);
        RoomScan scan = RoomScanner.scan(CENTRE, grid, 4096);
        assertNotNull(scan);
        assertEquals(27, scan.volume());
    }

    @Test
    void slabInTheWallLeaks() {
        // The slab only covers the bottom half of the face it shares with the outside.
        Grid grid = cube3().set(new BlockPos(0, 2, 2), BOTTOM_SLAB);
        assertNull(RoomScanner.scan(CENTRE, grid, 4096));
    }

    @Test
    void plateAcrossTheOuterFaceSeals() {
        // Air gets into the doorway cell but the plate covers its far side.
        Grid grid = cube3().set(new BlockPos(0, 2, 2), WEST_PLATE);
        RoomScan scan = RoomScanner.scan(CENTRE, grid, 4096);
        assertNotNull(scan);
        assertEquals(28, scan.volume());
        assertTrue(scan.boundary().contains(new BlockPos(-1, 2, 2).asLong()));
    }

    @Test
    void plateInsetFromTheFaceStillSeals() {
        // Like the Large Space Door: a full plate across the cell that touches neither face.
        Grid grid = cube3().set(new BlockPos(0, 2, 2), Shapes.box(1 / 16.0, 0, 0, 4 / 16.0, 1, 1));
        RoomScan scan = RoomScanner.scan(CENTRE, grid, 4096);
        assertNotNull(scan);
        assertEquals(28, scan.volume());
    }

    @Test
    void wallOfInsetPlatesSeals() {
        // A whole wall of them, each cell's sides open to its neighbour's on both sides of the plate.
        Grid grid = cube3();
        VoxelShape plate = Shapes.box(0, 0, 1 / 16.0, 1, 1, 4 / 16.0);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                grid.set(new BlockPos(x, y, 0), plate);
            }
        }
        RoomScan scan = RoomScanner.scan(CENTRE, grid, 4096);
        assertNotNull(scan);
        assertEquals(36, scan.volume());
    }

    @Test
    void plateAlongTheSideLeaks() {
        // An open door: the plate turned to run along the doorway, which leaves it open.
        Grid grid = cube3().set(new BlockPos(0, 2, 2), Shapes.box(0, 0, 0, 1, 1, 3 / 16.0));
        assertNull(RoomScanner.scan(CENTRE, grid, 4096));
    }

    @Test
    void tooLargeIsRefused() {
        assertNull(RoomScanner.scan(CENTRE, cube3(), 26));
        assertNotNull(RoomScanner.scan(CENTRE, cube3(), 27));
    }

    @Test
    void solidStartIsNoRoom() {
        assertNull(RoomScanner.scan(new BlockPos(0, 2, 2), cube3(), 4096));
    }

    @Test
    void holeIsNoRoom() {
        Grid grid = cube3();
        assertNull(RoomScanner.scan(new BlockPos(2, 40, 2), grid, 4096));
    }
}
