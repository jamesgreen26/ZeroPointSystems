package g_mungus.zps.recipe;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/** How a gas shows in a thruster's plume: orange fire, blue fire, or a cold puff with no fire. */
public enum ThrusterFlame implements StringRepresentable {
    ORANGE("orange"),
    BLUE("blue"),
    COLD("cold");

    public static final StringRepresentable.EnumCodec<ThrusterFlame> CODEC =
            StringRepresentable.fromEnum(ThrusterFlame::values);

    private final String name;

    ThrusterFlame(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
