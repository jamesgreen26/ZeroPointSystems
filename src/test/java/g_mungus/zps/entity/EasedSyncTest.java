package g_mungus.zps.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EasedSyncTest {

    /** A second into a fall: the step the next tick takes. */
    private static final Vec3 STEP = new Vec3(0.0, -0.69, 0.0);

    @Test
    void onTimeIsNotWrong() {
        assertEquals(0.0, EasedSync.residual(Vec3.ZERO, STEP).length(), 1e-9);
    }

    @Test
    void aTickEarlyIsNotWrong() {
        // The client is a step past the reported position, so the report is a step behind it.
        assertEquals(0.0, EasedSync.residual(STEP.scale(-1.0), STEP).length(), 1e-9);
    }

    @Test
    void aTickLateIsNotWrong() {
        assertEquals(0.0, EasedSync.residual(STEP, STEP).length(), 1e-9);
    }

    @Test
    void sidewaysErrorIsKeptWhateverTheTiming() {
        Vec3 sideways = new Vec3(0.3, 0.0, 0.0);
        assertEquals(sideways, EasedSync.residual(sideways, STEP));
        assertEquals(sideways, EasedSync.residual(sideways.subtract(STEP), STEP));
    }

    @Test
    void twoTicksOutLeavesOneToMakeUp() {
        assertEquals(STEP.length(), EasedSync.residual(STEP.scale(2.0), STEP).length(), 1e-9);
    }

    @Test
    void aBlockAtRestIsHeldToTheReport() {
        Vec3 error = new Vec3(0.0, 0.2, 0.0);
        assertEquals(error, EasedSync.residual(error, Vec3.ZERO));
    }
}
