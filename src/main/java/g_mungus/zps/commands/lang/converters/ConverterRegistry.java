package g_mungus.zps.commands.lang.converters;

import java.util.*;
import java.util.function.Function;

public final class ConverterRegistry {

    private static final Map<Key<?, ?>, Map<String, Converter<?, ?>>> CONVERTERS = new HashMap<>();

    private ConverterRegistry() {}

    public static <A, B> void register(
            Class<A> from,
            Class<B> to,
            String name,
            Function<A, B> converter
    ) {
        CONVERTERS
                .computeIfAbsent(new Key<>(from, to), k -> new LinkedHashMap<>())
                .put(name, new Converter<A, B>() {
                    @Override
                    public B convert(A value) {
                        return converter.apply(value);
                    }

                    @Override
                    public Class<B> getReturnType() {
                        return to;
                    }

                    @Override
                    public String getName() {
                        return name;
                    }
                });
    }

    @SuppressWarnings("unchecked")
    public static <A, B> Map<String, Converter<A, B>> get(
            Class<A> from,
            Class<B> to
    ) {
        return (Map<String, Converter<A, B>>) (Map<?, ?>)
                CONVERTERS.getOrDefault(new Key<>(from, to), Map.of());
    }

    private record Key<A, B>(Class<A> from, Class<B> to) {}
}
