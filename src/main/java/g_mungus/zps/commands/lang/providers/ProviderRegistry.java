package g_mungus.zps.commands.lang.providers;

import g_mungus.zps.commands.lang.converters.Converter;
import g_mungus.zps.commands.lang.converters.ProviderConverters;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProviderRegistry {

    private static final Map<Class<?>, Map<String, Provider<?>>> REGISTRY = new HashMap<>();

    private ProviderRegistry() {}

    public static <T> void register(
            Class<T> type,
            String name,
            Provider<T> provider
    ) {
        REGISTRY
                .computeIfAbsent(type, k -> new LinkedHashMap<>())
                .put(name, provider);
    }

    public static <A, B> void registerWithDerivatives(
            Class<A> baseType,
            String name,
            Provider<A> provider,
            Class<B> derivativeType,
            String... derivativeNames
    ) {
        // register base provider
        register(baseType, name, provider);

        Map<String, Converter<A, B>> converters =
                ProviderConverters.get(baseType, derivativeType);

        for (String derivative : derivativeNames) {

            Converter<A, B> converter = converters.get(derivative);
            if (converter == null) {
                throw new IllegalArgumentException(
                        "No converter registered: " +
                                baseType.getSimpleName() + " -> " +
                                derivativeType.getSimpleName() + " : " + derivative
                );
            }

            register(
                    derivativeType,
                    name + " " + derivative,
                    ctx -> converter.convert(provider.get(ctx))
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, Provider<T>> getAll(Class<T> type) {
        return (Map<String, Provider<T>>) (Map<?, ?>)
                REGISTRY.getOrDefault(type, Map.of());
    }
}

