package g_mungus.zps.block.cableNetwork.properties;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public enum InsulationType implements StringRepresentableWithAliases {
    NONE("none", Set.of("false"), false),
    INSULATION("insulation", Set.of("true"), true),
    CATWALK("catwalk", Set.of(), false),
    GRATING("grating", Set.of(), true);

    private final String name;
    private final Set<String> aliases;
    private final boolean insulatesAllSides;

    InsulationType(String name, Set<String> aliases, boolean insulatesAllSides) {
        this.name = name;
        this.aliases = aliases;
        this.insulatesAllSides = insulatesAllSides;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }

    @Override
    public Set<String> getAliases() {
        return aliases;
    }

    public boolean insulatesAllSides() {
        return insulatesAllSides;
    }
}
