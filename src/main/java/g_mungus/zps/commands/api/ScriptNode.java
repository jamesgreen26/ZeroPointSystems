package g_mungus.zps.commands.api;

sealed public interface ScriptNode permits ScriptExecutor, ScriptGetter, ScriptMapper {
    String displayName();
}
