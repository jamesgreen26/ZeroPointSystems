package g_mungus.zps.commands.lang.converters;

import java.util.*;

public final class ProviderConverters {

    private static final Map<Key<?, ?>, Map<String, Converter<?, ?>>> CONVERTERS = new HashMap<>();

    private ProviderConverters() {}

    public static <A, B> void register(
            Class<A> from,
            Class<B> to,
            String name,
            Converter<A, B> converter
    ) {
        CONVERTERS
                .computeIfAbsent(new Key<>(from, to), k -> new LinkedHashMap<>())
                .put(name, converter);
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
