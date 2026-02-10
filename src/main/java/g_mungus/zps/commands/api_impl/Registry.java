package g_mungus.zps.commands.api_impl;

import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptNode;

import java.util.HashSet;
import java.util.Set;

class Registry {

    static Set<ScriptExecutor<?>> EXECUTORS = new HashSet<>();
    static Set<ScriptGetter<?>> GETTERS = new HashSet<>();
    static Set<ScriptMapper<?, ?>> MAPPERS = new HashSet<>();


    static void register(ScriptNode node) {
        if (node instanceof ScriptExecutor<?> executor) {
            EXECUTORS.add(executor);
        } else if (node instanceof ScriptMapper<?,?> mapper) {
            MAPPERS.add(mapper);
        } else if (node instanceof ScriptGetter<?> getter) {
            GETTERS.add(getter);
        }
    }
}
