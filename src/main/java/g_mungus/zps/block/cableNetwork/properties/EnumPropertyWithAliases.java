package g_mungus.zps.block.cableNetwork.properties;

import com.google.common.collect.Maps;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EnumPropertyWithAliases<T extends Enum<T> & StringRepresentableWithAliases> extends EnumProperty<T> {

    private final Map<String, T> aliases = Maps.newHashMap();

    protected EnumPropertyWithAliases(String name, Class<T> valueClass, Collection<T> values) {
        super(name, valueClass, values);

        for (T value : values) {
            for (String alias : value.getAliases()) {
                if (this.aliases.containsKey(alias)) {
                    throw new IllegalArgumentException("Multiple values have the same alias '" + alias + "'");
                }
                this.aliases.put(alias, value);
            }
        }
    }


    public @NotNull Optional<@NotNull T> getValue(@NotNull String key) {
        Optional<@NotNull T> value = super.getValue(key);

        if (value.isPresent()) {
            return value;
        } else {
            return Optional.ofNullable(this.aliases.get(key));
        }
    }

    public static <T extends Enum<T> & StringRepresentableWithAliases> EnumPropertyWithAliases<T> createWithAliases(String name, Class<T> valueClass) {
        return createWithAliases(name, valueClass, value -> true);
    }

    public static <T extends Enum<T> & StringRepresentableWithAliases> EnumPropertyWithAliases<T> createWithAliases(String name, Class<T> valueClass, Predicate<T> filter) {
        return createWithAliases(name, valueClass, Arrays.stream(valueClass.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    public static <T extends Enum<T> & StringRepresentableWithAliases> EnumPropertyWithAliases<T> createWithAliases(String name, Class<T> valueClass, Collection<T> values) {
        return new EnumPropertyWithAliases<>(name, valueClass, values);
    }
}
