package g_mungus.zps.commands.lang.providers;

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

    @SuppressWarnings("unchecked")
    public static <T> Map<String, Provider<T>> getAll(Class<T> type) {
        return (Map<String, Provider<T>>) (Map<?, ?>)
                REGISTRY.getOrDefault(type, Map.of());
    }
}

