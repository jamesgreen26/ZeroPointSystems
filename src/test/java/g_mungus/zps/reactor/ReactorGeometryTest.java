package g_mungus.zps.reactor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReactorGeometryTest {

    @Test
    void wallHeatCapacityScalesWithWalls() {
        assertEquals(8100.0, ReactorGeometry.wallHeatCapacity(54, 150.0), 1e-9);
    }
}
