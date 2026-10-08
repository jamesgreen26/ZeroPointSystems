package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.type.ScriptType;
import net.neoforged.bus.api.Event;

import java.util.function.Consumer;

/**
 * Collects script types beyond ZPS's own, such as a ship. Posted on both sides: the server builds
 * its scripts with these, and a client reads the tree the server sends with them, so a type has to
 * be registered the same way on both.
 */
public class RegisterScriptTypesEvent extends Event {
    private final Consumer<ScriptType<?>> registrar;

    public RegisterScriptTypesEvent(Consumer<ScriptType<?>> registrar) {
        this.registrar = registrar;
    }

    public void register(ScriptType<?> type) {
        registrar.accept(type);
    }
}
