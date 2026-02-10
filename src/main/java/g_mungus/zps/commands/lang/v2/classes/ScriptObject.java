package g_mungus.zps.commands.lang.v2.classes;

public record ScriptObject<T>(String name, T value, ScriptClass<T> type) {

    public static <T> ScriptObject<T> withDefaultType(String name, T value) {
        return new ScriptObject<>(name, value, ScriptClassRegistry.getDefault(value.getClass()));
    }
}
