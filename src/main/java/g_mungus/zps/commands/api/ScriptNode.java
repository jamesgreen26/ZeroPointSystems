package g_mungus.zps.commands.api;

public sealed interface ScriptNode permits ScriptExecutor, ScriptGetter, ScriptMapper {
    String displayName();
}
