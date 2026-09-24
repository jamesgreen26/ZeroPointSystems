package g_mungus.zps.reactor;

import g_mungus.zps.reactor.CavityScanner.Cell;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scanner on a map-backed grid: sealed shells are found with the right walls and host,
 * anything with a hole or over the size limit is refused, and blocks that are not part of the
 * shell are ignored.
 */
public class CavityScannerTest {

    private static final int MAX_EXTENT = 14;

    /** A world where everything is air until told otherwise. */
    private static class Grid implements Function<BlockPos, Cell> {
        private final Map<BlockPos, Cell> cells = new HashMap<>();

        Grid set(BlockPos pos, Cell cell) {
            cells.put(pos.immutable(), cell);
            return this;
        }

        /** A hollow box of wall whose interior spans {@code from} to {@code to} inclusive. */
        Grid shell(BlockPos from, BlockPos to) {
            for (int x = from.getX() - 1; x <= to.getX() + 1; x++) {
                for (int y = from.getY() - 1; y <= to.getY() + 1; y++) {
                    for (int z = from.getZ() - 1; z <= to.getZ() + 1; z++) {
                        boolean inside = x >= from.getX() && x <= to.getX()
                                && y >= from.getY() && y <= to.getY()
                                && z >= from.getZ() && z <= to.getZ();
                        if (!inside) {
                            set(new BlockPos(x, y, z), Cell.WALL);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        public Cell apply(BlockPos pos) {
            return cells.getOrDefault(pos, Cell.AIR);
        }
    }

    private static Grid cube3() {
        return new Grid().shell(new BlockPos(1, 1, 1), new BlockPos(3, 3, 3));
    }

    @Test
    void sealedCubeIsFound() {
        CavityScan scan = CavityScanner.scan(new BlockPos(2, 2, 2), cube3(), MAX_EXTENT);

        assertNotNull(scan);
        assertEquals(27, scan.volume());
        assertEquals(54, scan.wallCount(), "only the blocks with a face on the cavity: 6 faces of 9");
        assertEquals(new BlockPos(1, 1, 1), scan.host(), "host is the lowest interior cell");
    }

    @Test
    void startMustBeAir() {
        assertNull(CavityScanner.scan(new BlockPos(0, 0, 0), cube3(), MAX_EXTENT));
    }

    @Test
    void oneMissingBlockLeaks() {
        Grid grid = cube3().set(new BlockPos(4, 2, 2), Cell.AIR);

        assertNull(CavityScanner.scan(new BlockPos(2, 2, 2), grid, MAX_EXTENT));
    }

    @Test
    void foreignBlockInTheShellLeaks() {
        Grid grid = cube3().set(new BlockPos(4, 2, 2), Cell.OTHER);

        assertNull(CavityScanner.scan(new BlockPos(2, 2, 2), grid, MAX_EXTENT));
    }

    @Test
    void foreignBlockInsideIsNotACavity() {
        Grid grid = cube3().set(new BlockPos(2, 2, 2), Cell.OTHER);

        assertNull(CavityScanner.scan(new BlockPos(1, 1, 1), grid, MAX_EXTENT));
    }

    @Test
    void tooLongIsRefused() {
        Grid within = new Grid().shell(new BlockPos(1, 1, 1), new BlockPos(MAX_EXTENT, 1, 1));
        Grid over = new Grid().shell(new BlockPos(1, 1, 1), new BlockPos(MAX_EXTENT + 1, 1, 1));

        assertNotNull(CavityScanner.scan(new BlockPos(1, 1, 1), within, MAX_EXTENT));
        assertNull(CavityScanner.scan(new BlockPos(1, 1, 1), over, MAX_EXTENT));
    }

    @Test
    void lShapeIsOneCavity() {
        // Two overlapping boxes: one along x, one along z, sharing the corner cell (1,1,1).
        Grid grid = new Grid()
                .shell(new BlockPos(1, 1, 1), new BlockPos(4, 1, 1))
                .shell(new BlockPos(1, 1, 1), new BlockPos(1, 1, 4));
        // The overlapping shells left wall in the shared interior; clear it.
        for (int x = 1; x <= 4; x++) {
            grid.set(new BlockPos(x, 1, 1), Cell.AIR);
        }
        for (int z = 1; z <= 4; z++) {
            grid.set(new BlockPos(1, 1, z), Cell.AIR);
        }

        CavityScan scan = CavityScanner.scan(new BlockPos(4, 1, 1), grid, MAX_EXTENT);

        assertNotNull(scan);
        assertEquals(7, scan.volume());
        assertEquals(new BlockPos(1, 1, 1), scan.host());
    }

    @Test
    void extraOuterLayerIsNotPartOfTheWall() {
        Grid grid = cube3();
        // A full second layer around the shell.
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -1; z <= 5; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (grid.apply(pos) == Cell.AIR && (x == -1 || x == 5 || y == -1 || y == 5 || z == -1 || z == 5)) {
                        grid.set(pos, Cell.WALL);
                    }
                }
            }
        }

        CavityScan scan = CavityScanner.scan(new BlockPos(2, 2, 2), grid, MAX_EXTENT);

        assertNotNull(scan);
        assertEquals(54, scan.wallCount(), "the outer layer never touches the interior");
    }

    @Test
    void sharedWallBelongsToBothCavities() {
        // Two 1x1x1 cavities at x=1 and x=3 with the single wall block at x=2 between them.
        Grid grid = new Grid()
                .shell(new BlockPos(1, 1, 1), new BlockPos(1, 1, 1))
                .shell(new BlockPos(3, 1, 1), new BlockPos(3, 1, 1));
        BlockPos shared = new BlockPos(2, 1, 1);

        CavityScan left = CavityScanner.scan(new BlockPos(1, 1, 1), grid, MAX_EXTENT);
        CavityScan right = CavityScanner.scan(new BlockPos(3, 1, 1), grid, MAX_EXTENT);

        assertNotNull(left);
        assertNotNull(right);
        assertTrue(left.walls().contains(shared.asLong()));
        assertTrue(right.walls().contains(shared.asLong()));
        assertEquals(6, left.wallCount(), "a 1x1x1 cavity has six wall faces");
    }

    @Test
    void scanAroundFindsEachCavityOnce() {
        // The shared wall is the block just changed: both cavities should come back, once each.
        Grid grid = new Grid()
                .shell(new BlockPos(1, 1, 1), new BlockPos(1, 1, 1))
                .shell(new BlockPos(3, 1, 1), new BlockPos(3, 1, 1));

        List<CavityScan> found = CavityScanner.scanAround(new BlockPos(2, 1, 1), grid, MAX_EXTENT);

        assertEquals(2, found.size());
        assertNotEquals(found.get(0).host(), found.get(1).host());
    }

    @Test
    void scanAroundInOpenAirFindsNothing() {
        // A lone wall block in the open. Six air neighbours all lead to the same open field.
        Grid grid = new Grid().set(new BlockPos(0, 0, 0), Cell.WALL);

        assertTrue(CavityScanner.scanAround(new BlockPos(0, 0, 0), grid, 4).isEmpty());
    }

    @Test
    void scanAroundIncludesTheChangedCellWhenItIsAir() {
        // The block at (2,2,2) inside the cube was just removed, so the scan starts there too.
        Grid grid = cube3();

        List<CavityScan> found = CavityScanner.scanAround(new BlockPos(2, 2, 2), grid, MAX_EXTENT);

        assertEquals(1, found.size());
        assertEquals(27, found.get(0).volume());
    }
}
