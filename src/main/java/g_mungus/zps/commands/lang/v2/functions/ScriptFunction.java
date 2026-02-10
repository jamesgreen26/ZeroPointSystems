package g_mungus.zps.commands.lang.v2.functions;

import g_mungus.zps.commands.lang.v2.ScriptContext;
import g_mungus.zps.commands.lang.v2.classes.ScriptObject;

import java.util.function.BiFunction;
import java.util.function.Function;

public record ScriptFunction<I, O> (String name, BiFunction<ScriptObject<I>, ScriptContext, ScriptObject<O>> function) {

    public static <I, O> ScriptFunction<I, O> simple(String name, Function<ScriptObject<I>, ScriptObject<O>> function) {
        return new ScriptFunction<>(name, (a, b) -> function.apply(a));
    }
}
