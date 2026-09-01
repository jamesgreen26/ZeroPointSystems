package g_mungus.zps.client.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The overlay is only useful if the colour ramp actually distinguishes pressures across the range
 * a gas network works in, which spans several orders of magnitude — and if a run of adjacent nodes
 * does not smear into z-fighting where their tints meet.
 */
public class GasPressureOverlayTest {

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    @Test
    void anEmptyNodeReadsBlue() {
        int colour = GasPressureOverlay.colourFor(0);
        assertTrue(blue(colour) > red(colour),
                "an empty node should sit at the blue end, got " + Integer.toHexString(colour));
    }

    @Test
    void aNodeAtTheCeilingReadsRed() {
        int colour = GasPressureOverlay.colourFor(16_375_049.0);
        assertTrue(red(colour) > blue(colour),
                "a node at its ceiling should sit at the red end, got " + Integer.toHexString(colour));
    }

    @Test
    void theRampSeparatesOrdinaryWorkingPressures() {
        // A vaporizer batch is around 0.9 MPa and its buffer stalls near 3.6 MPa. On a linear ramp
        // against a 16 MPa ceiling those would be almost the same colour.
        int oneBatch = GasPressureOverlay.colourFor(919_000.0);
        int nearlyFull = GasPressureOverlay.colourFor(3_600_000.0);

        assertNotEquals(oneBatch, nearlyFull, "working pressures must be distinguishable");
        assertTrue(red(nearlyFull) >= red(oneBatch),
                "a fuller node should read no cooler than an emptier one");
    }

    @Test
    void everyStepOfTheWorkingRangeGetsItsOwnColour() {
        // If two pressures a player will routinely see share a colour, the overlay is not telling
        // them anything.
        double[] pressures = {0, 1_000, 100_000, 919_000, 3_600_000, 16_375_049.0};
        Set<Integer> colours = new HashSet<>();
        for (double pressure : pressures) {
            assertTrue(colours.add(GasPressureOverlay.colourFor(pressure)),
                    "two pressures in the working range share a colour, at " + pressure);
        }
    }

    @Test
    void aLoneNodeIsDrawnProudOfItsBlockOnEverySide() {
        double[] box = GasPressureOverlay.boxFor(new BlockPos(4, 5, 6), Set.of());

        assertArrayEquals(new double[]{
                4 - GasPressureOverlay.INFLATE, 5 - GasPressureOverlay.INFLATE, 6 - GasPressureOverlay.INFLATE,
                5 + GasPressureOverlay.INFLATE, 6 + GasPressureOverlay.INFLATE, 7 + GasPressureOverlay.INFLATE
        }, box, 1e-9, "a node with nothing beside it should clear the model on all six faces");
    }

    @Test
    void aFaceWithANodeAgainstItIsTrimmedFlush() {
        BlockPos middle = new BlockPos(0, 0, 0);
        double[] box = GasPressureOverlay.boxFor(middle, Set.of(middle, middle.east()));

        assertEquals(1.0, box[3], 1e-9,
                "the face a neighbour sits against must stop at the block boundary, not overlap it");
        assertEquals(-GasPressureOverlay.INFLATE, box[0], 1e-9,
                "the opposite face has nothing against it and should still be inflated");
    }

    @Test
    void neighbouringNodesMeetWithoutOverlapping() {
        // The case that produced the z-fighting: a straight run of duct, every node inflated into
        // the next one's space.
        BlockPos left = new BlockPos(0, 0, 0);
        BlockPos right = left.east();
        Set<BlockPos> run = Set.of(left, right);

        double leftMaxX = GasPressureOverlay.boxFor(left, run)[3];
        double rightMinX = GasPressureOverlay.boxFor(right, run)[0];

        assertEquals(leftMaxX, rightMinX, 1e-9,
                "adjacent boxes must share one plane rather than overlap in a doubly-blended shell");
    }

    @Test
    void aNodeWalledInOnEverySideIsDrawnAtExactlyBlockSize() {
        BlockPos middle = new BlockPos(2, 2, 2);
        Set<BlockPos> surrounded = new HashSet<>();
        surrounded.add(middle);
        for (Direction direction : Direction.values()) {
            surrounded.add(middle.relative(direction));
        }

        assertArrayEquals(new double[]{2, 2, 2, 3, 3, 3},
                GasPressureOverlay.boxFor(middle, surrounded), 1e-9,
                "a node with neighbours all round has nothing to clear and should sit flush");
    }

    @Test
    void pressuresBeyondTheCeilingStayAtTheTopOfTheRamp() {
        assertEquals(GasPressureOverlay.colourFor(16_375_049.0),
                GasPressureOverlay.colourFor(50_000_000.0),
                "the ramp must clamp rather than wrap back around the hue circle");
    }
}
