package g_mungus.zps.commands.lang.v2.classes;

import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;

import java.util.List;

public interface ScriptClass<T> {
    String name();

    Class<T> getType();

    MappedArgumentType<?, T> getArgumentType();

    List<ScriptComparator<T>> getComparators();

    List<ScriptFunction<T, ?>> getFunctions();
}
