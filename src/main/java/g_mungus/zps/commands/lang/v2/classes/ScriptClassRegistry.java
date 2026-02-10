package g_mungus.zps.commands.lang.v2.classes;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScriptClassRegistry {
    private static final Map<Class<?>, Map<String, ScriptClass<?>>> REGISTRY = new HashMap<>();

    public static <T> void register(ScriptClass<T> scriptClass) {
        REGISTRY.computeIfAbsent(scriptClass.getType(), (c) -> new LinkedHashMap<>())
                .put(scriptClass.name(), scriptClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> ScriptClass<T> getDefault(Class<?> aClass) {
        return (ScriptClass<T>) REGISTRY.get(aClass).values().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Script Class not registered for type: " + aClass.getName()));
    }

    @SuppressWarnings("unchecked")
    public static <T> ScriptClass<T> get(Class<?> aClass, String name) {
        try {
            return (ScriptClass<T>) REGISTRY.get(aClass).get(name);
        } catch (Exception e) {
            throw new RuntimeException("Script Class not registered for type: " + aClass.getName());
        }
    }

    public static void bootstrap() {
        register(new BlockPosClass("POS"));
        register(new IntegerClass("INTEGER"));
        register(new DoubleClass("DOUBLE"));
    }
}
