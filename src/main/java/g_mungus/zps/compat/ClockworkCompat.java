package g_mungus.zps.compat;

import org.valkyrienskies.clockwork.ClockworkGasses;
import org.valkyrienskies.kelvin.api.GasType;

public class ClockworkCompat {
    public static GasType getAetherGas() {
        return ClockworkGasses.INSTANCE.getHELIUM();
    }
}
