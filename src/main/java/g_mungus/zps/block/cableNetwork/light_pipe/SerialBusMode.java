package g_mungus.zps.block.cableNetwork.light_pipe;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * What a {@link SerialBusBlock} does with the text it receives. Chosen in the block's screen and
 * kept on the block entity, since it changes nothing about how the block looks.
 */
public enum SerialBusMode implements StringRepresentable {
    /** Runs each message as a script command against the block it faces. */
    EXECUTE("execute"),
    /**
     * Reads a value from the block it faces every few ticks, through a getter to mapper chain the
     * player writes on the block's screen, and puts the answer on the light pipe.
     */
    GET("get");

    private final String serializedName;

    SerialBusMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }

    public SerialBusMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SerialBusMode byName(String name) {
        for (SerialBusMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return EXECUTE;
    }

    /** Translation key of the mode's display name. */
    public String translationKey() {
        return "gui.zps.serial_bus.mode." + serializedName;
    }
}
