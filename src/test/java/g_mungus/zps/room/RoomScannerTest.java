package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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

    // --- growing ----------------------------------------------------------------------------

    private record Grown(RoomScanner.Growth growth, LongSet added, LongSet boundary, Long2LongMap pockets) {
    }

    private static Grown grow(RoomScan room, Grid grid, int maxVolume, BlockPos... changed) {
        LongSet changedCells = new LongOpenHashSet();
        for (BlockPos pos : changed) {
            changedCells.add(pos.asLong());
        }
        LongSet added = new LongOpenHashSet();
        LongSet boundary = new LongOpenHashSet();
        Long2LongMap pockets = new Long2LongOpenHashMap();
        RoomScanner.Growth growth = RoomScanner.grow(room.interior(), room.pockets(), changedCells, grid, maxVolume,
                added, boundary, pockets);
        return new Grown(growth, added, boundary, pockets);
    }

    @Test
    void diggingThroughTheWallEscapes() {
        Grid grid = cube3();
        RoomScan room = RoomScanner.scan(CENTRE, grid, 4096);
        BlockPos wall = new BlockPos(0, 2, 2);
        grid.set(wall, Shapes.empty());
        assertEquals(RoomScanner.Growth.ESCAPED, grow(room, grid, 4096, wall).growth());
    }

    @Test
    void diggingIntoAThickWallGrowsByTheHole() {
        BlockPos wall = new BlockPos(0, 2, 2);
        Grid grid = cube3().set(wall.west(), Shapes.block());
        RoomScan room = RoomScanner.scan(CENTRE, grid, 4096);
        grid.set(wall, Shapes.empty());

        Grown grown = grow(room, grid, 4096, wall);
        assertEquals(RoomScanner.Growth.GREW, grown.growth());
        assertEquals(java.util.Set.of(wall.asLong()), new java.util.HashSet<>(grown.added()));
        assertTrue(grown.boundary().contains(wall.west().asLong()));

        // The same room a fresh flood finds.
        LongSet grownInterior = new LongOpenHashSet(room.interior());
        grownInterior.addAll(grown.added());
        assertEquals(RoomScanner.scan(CENTRE, grid, 4096).interior(), grownInterior);
    }

    @Test
    void diggingIntoASealedCavityTakesItIn() {
        // A second sealed box, 2x2x2, the other side of the west wall.
        Grid grid = cube3().shell(new BlockPos(-2, 2, 2), new BlockPos(-1, 3, 3));
        grid.set(new BlockPos(0, 2, 2), Shapes.block()).set(new BlockPos(0, 3, 3), Shapes.block());
        RoomScan room = RoomScanner.scan(CENTRE, grid, 4096);
        assertEquals(27, room.volume());

        BlockPos wall = new BlockPos(0, 2, 2);
        grid.set(wall, Shapes.empty());
        Grown grown = grow(room, grid, 4096, wall);
        assertEquals(RoomScanner.Growth.GREW, grown.growth());
        assertEquals(9, grown.added().size());
    }

    @Test
    void growingPastTheLimitEscapes() {
        BlockPos wall = new BlockPos(0, 2, 2);
        Grid grid = cube3().set(wall.west(), Shapes.block());
        RoomScan room = RoomScanner.scan(CENTRE, grid, 27);
        grid.set(wall, Shapes.empty());
        assertEquals(RoomScanner.Growth.ESCAPED, grow(room, grid, 27, wall).growth());
    }

    /**
     * The room holds only the inner pocket of a cell with a plate across it, so the outside opening
     * up past the plate is nothing to do with it.
     */
    @Test
    void openingUpBeyondAnInsetPlateLeavesTheRoomAlone() {
        BlockPos door = new BlockPos(0, 2, 2);
        BlockPos outside = door.west();
        Grid grid = cube3().set(door, Shapes.box(1 / 16.0, 0, 0, 4 / 16.0, 1, 1)).set(outside, Shapes.block());
        RoomScan room = RoomScanner.scan(CENTRE, grid, 4096);
        assertTrue(room.pockets().containsKey(door.asLong()), "The door's cell should be only partly in the room");

        grid.set(outside, Shapes.empty());
        Grown grown = grow(room, grid, 4096, outside);
        assertEquals(RoomScanner.Growth.GREW, grown.growth());
        assertTrue(grown.added().isEmpty());
    }

    @Test
    void openingTheInsetPlateEscapes() {
        BlockPos door = new BlockPos(0, 2, 2);
        Grid grid = cube3().set(door, Shapes.box(1 / 16.0, 0, 0, 4 / 16.0, 1, 1));
        RoomScan room = RoomScanner.scan(CENTRE, grid, 4096);
        grid.set(door, Shapes.empty());
        assertEquals(RoomScanner.Growth.ESCAPED, grow(room, grid, 4096, door).growth());
    }
}
