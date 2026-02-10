package g_mungus.zps.commands.lang.comparators;

import java.util.*;

public final class ComparisonRegistry {

    private static final Map<Class<?>, Map<String, Comparison<?>>> REGISTRY = new HashMap<>();

    private ComparisonRegistry() {}

    public static <T> void register(
            Class<T> type,
            String name,
            Comparison<T> comparison
    ) {
        REGISTRY
                .computeIfAbsent(type, k -> new LinkedHashMap<>())
                .put(name, comparison);
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, Comparison<T>> getAll(Class<T> type) {
        return (Map<String, Comparison<T>>) (Map<?, ?>)
                REGISTRY.getOrDefault(type, Map.of());
    }
}
