package g_mungus.zps.gas.api;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeatSourceTest {

    private static final HeatSource STAR = new HeatSource(Vec3.ZERO, 950, 720);

    @Test
    void isSurfaceTemperatureAtAndWithinItsRadius() {
        assertEquals(950, STAR.temperatureAt(720), 1e-9);
        assertEquals(950, STAR.temperatureAt(0), 1e-9);
    }

    @Test
    void fallsOffWithTheSquareRootOfDistance() {
        assertEquals(475, STAR.temperatureAt(4 * 720), 1e-9);
        assertEquals(950 * Math.sqrt(720.0 / 15000), STAR.temperatureAt(15000), 1e-9);
    }

    @Test
    void needsAPositiveRadius() {
        assertThrows(IllegalArgumentException.class, () -> new HeatSource(Vec3.ZERO, 950, 0));
        assertThrows(IllegalArgumentException.class, () -> new HeatSource(Vec3.ZERO, 950, Double.NaN));
    }

    @Test
    void aSteeperFalloff_coolsFasterWithDistance() {
        HeatSource hot = new HeatSource(Vec3.ZERO, 250_000, 720, 2);
        assertEquals(250_000, hot.temperatureAt(720), 1e-6);
        assertEquals(250_000 / 16.0, hot.temperatureAt(4 * 720), 1e-6);
    }

    @Test
    void needsAPositiveFalloff() {
        assertThrows(IllegalArgumentException.class, () -> new HeatSource(Vec3.ZERO, 950, 720, 0));
        assertThrows(IllegalArgumentException.class, () -> new HeatSource(Vec3.ZERO, 950, 720, Double.NaN));
    }
}
