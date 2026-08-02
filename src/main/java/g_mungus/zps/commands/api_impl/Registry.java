package g_mungus.zps.commands.api_impl;

import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptNode;

import java.util.LinkedHashSet;
import java.util.Set;

class Registry {

    static Set<ScriptExecutor<?, ?>> EXECUTORS = new LinkedHashSet<>();
    static Set<ScriptGetter<?>> GETTERS = new LinkedHashSet<>();
    static Set<ScriptMapper<?, ?>> MAPPERS = new LinkedHashSet<>();


    static void clear() {
        EXECUTORS.clear();
        GETTERS.clear();
        MAPPERS.clear();
    }

    static void register(ScriptNode node) {
        if (node instanceof ScriptExecutor<?, ?> executor) {
            EXECUTORS.add(executor);
        } else if (node instanceof ScriptMapper<?,?> mapper) {
            MAPPERS.add(mapper);
        } else if (node instanceof ScriptGetter<?> getter) {
            GETTERS.add(getter);
        }
    }
}
