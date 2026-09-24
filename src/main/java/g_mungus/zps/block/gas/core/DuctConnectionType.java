package g_mungus.zps.block.gas.core;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * How one face of a gas node relates to its neighbour: not joined at all, joined by a real
 * Kelvin edge, or open to the world so gas escapes.
 */
public enum DuctConnectionType implements StringRepresentable {
    NONE, CONNECTION, LEAK;

    @Override
    public @NotNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
