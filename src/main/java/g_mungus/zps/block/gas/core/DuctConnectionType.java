package g_mungus.zps.block.gas.core;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum DuctConnectionType implements StringRepresentable {
    NONE, CONNECTION, LEAK;

    @Override
    public @NotNull String getSerializedName() {
        return this.name();
    }
}
