package g_mungus.zps.block.cableNetwork.properties;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public enum InsulationType implements StringRepresentableWithAliases {
    NONE("none", Set.of("false")),
    INSULATION("insulation", Set.of("true")),
    CATWALK("catwalk"),
    GRATING("grating");

    private final String name;
    private final Set<String> aliases;

    InsulationType(String name, Set<String> aliases) {
        this.name = name;
        this.aliases = aliases;
    }

    InsulationType(String name) {
        this(name, Set.of());
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }

    @Override
    public Set<String> getAliases() {
        return aliases;
    }
}
