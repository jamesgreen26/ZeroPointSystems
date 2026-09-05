package g_mungus.zps.client.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Which faces of each cell get a coat: exactly the ones with a wall behind them. Works on the
 * vanilla grid directly, since the shape wrappers need mixins that only exist in-game.
 */
public class ClientReactorFacesTest {

    private static BitSetDiscreteVoxelShape cube(int size) {
        BitSetDiscreteVoxelShape grid = new BitSetDiscreteVoxelShape(size, size, size);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    grid.fill(x, y, z);
                }
            }
        }
        return grid;
    }

    private static int bit(Direction direction) {
        return 1 << direction.ordinal();
    }

    @Test
    void singleCellHasAllSixFaces() {
        BitSetDiscreteVoxelShape grid = new BitSetDiscreteVoxelShape(1, 1, 1);
        grid.fill(0, 0, 0);
        var masks = ClientReactor.faceMasks(grid, BlockPos.ZERO);
        assertEquals(0b111111, masks.get(BlockPos.asLong(0, 0, 0)));
    }

    @Test
    void cubeCentreDrawsNothing() {
        var masks = ClientReactor.faceMasks(cube(3), BlockPos.ZERO);
        assertFalse(masks.containsKey(BlockPos.asLong(1, 1, 1)));
        assertEquals(26, masks.size());
    }

    @Test
    void faceCellHasOneWall() {
        var masks = ClientReactor.faceMasks(cube(3), BlockPos.ZERO);
        assertEquals(bit(Direction.DOWN), masks.get(BlockPos.asLong(1, 0, 1)));
        assertEquals(bit(Direction.EAST), masks.get(BlockPos.asLong(2, 1, 1)));
    }

    @Test
    void cornerCellHasThreeWalls() {
        var masks = ClientReactor.faceMasks(cube(3), BlockPos.ZERO);
        assertEquals(bit(Direction.DOWN) | bit(Direction.NORTH) | bit(Direction.WEST), masks.get(BlockPos.asLong(0, 0, 0)));
    }

    @Test
    void originOffsetsEveryCell() {
        var masks = ClientReactor.faceMasks(cube(2), new BlockPos(10, -5, 7));
        assertEquals(8, masks.size());
        assertEquals(bit(Direction.DOWN) | bit(Direction.NORTH) | bit(Direction.WEST), masks.get(BlockPos.asLong(10, -5, 7)));
        assertEquals(bit(Direction.UP) | bit(Direction.SOUTH) | bit(Direction.EAST), masks.get(BlockPos.asLong(11, -4, 8)));
    }
}
