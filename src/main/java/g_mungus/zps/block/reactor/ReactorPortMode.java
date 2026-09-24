package g_mungus.zps.block.reactor;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Which way a {@link ReactorPortBlock} carries gas across the reactor wall. Chosen in the block's
 * screen and stored on the block state, so both the block and the client can read it without a
 * block entity lookup. The mode decides direction and the face overlay only; what the port lets
 * through is the block entity's gas filter, set separately.
 */
public enum ReactorPortMode implements StringRepresentable {
    /** Lets gas into the chamber and never out. */
    INPUT("input"),
    /** Pumps gas out of the chamber and never lets any in. */
    OUTPUT("output");

    private final String serializedName;

    ReactorPortMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }

    public ReactorPortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Translation key of the mode's display name. */
    public String translationKey() {
        return "gui.zps.reactor_port.mode." + serializedName;
    }
}
