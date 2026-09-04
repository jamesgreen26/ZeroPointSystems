package g_mungus.zps.reactor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReactorGeometryTest {

    private static final double BASE = 24_000_000.0;

    @Test
    void cubeIsFullyCompact() {
        assertEquals(1.0, ReactorGeometry.compactness(27, 54), 1e-9);
        assertEquals(1.0, ReactorGeometry.compactness(1, 6), 1e-9);
    }

    @Test
    void longerShapesAreLessCompact() {
        // 3x3x10 interior: 90 cells, 2*(9 + 30 + 30) = 138 walls.
        double bar = ReactorGeometry.compactness(90, 138);
        // 1x1x10 tunnel: 10 cells, 2*(1 + 10 + 10) = 42 walls.
        double tunnel = ReactorGeometry.compactness(10, 42);

        assertTrue(bar < 1.0);
        assertTrue(tunnel < bar, "a tunnel rates below a bar");
    }

    @Test
    void sizeFactorFallsWithVolumeAndClamps() {
        assertEquals(1.0, ReactorGeometry.sizeFactor(27, 0.1), 1e-9);
        assertEquals(1.0, ReactorGeometry.sizeFactor(1, 0.1), 1e-9, "small reactors never rate above one");
        double large = ReactorGeometry.sizeFactor(14 * 14 * 14, 0.1);
        assertTrue(large < 1.0 && large >= 0.5);
        assertEquals(0.5, ReactorGeometry.sizeFactor(Integer.MAX_VALUE, 4.0), 1e-9);
    }

    @Test
    void burstPressureIsBaseForTheReferenceCube() {
        assertEquals(BASE, ReactorGeometry.burstPressure(27, 54, BASE, 2.0, 0.1), 1e-6);
    }

    @Test
    void burstPressureDropsForPoorShapes() {
        double cube = ReactorGeometry.burstPressure(27, 54, BASE, 2.0, 0.1);
        double bar = ReactorGeometry.burstPressure(90, 138, BASE, 2.0, 0.1);
        double tunnel = ReactorGeometry.burstPressure(10, 42, BASE, 2.0, 0.1);

        assertTrue(bar < cube);
        assertTrue(tunnel < bar);
    }

    @Test
    void exponentsOfZeroDisableTheRatings() {
        assertEquals(BASE, ReactorGeometry.burstPressure(10, 42, BASE, 0.0, 0.0), 1e-6);
    }

    @Test
    void wallHeatCapacityScalesWithWalls() {
        assertEquals(8100.0, ReactorGeometry.wallHeatCapacity(54, 150.0), 1e-9);
    }
}
