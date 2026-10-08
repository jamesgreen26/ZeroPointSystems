package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.type.ScriptType;
import net.neoforged.bus.api.Event;

import java.util.function.Consumer;

/**
 * Collects script types beyond ZPS's own, such as a ship, and kinds of {@link TargetApplicability}
 * beyond {@link BlockApplicability}. Posted on both sides: the server builds its scripts with
 * these, and a client reads the tree the server sends with them, so each has to be registered the
 * same way on both.
 */
public class RegisterScriptTypesEvent extends Event {
    private final Consumer<ScriptType<?>> types;
    private final Consumer<TargetApplicability.Type<?>> applicabilities;

    public RegisterScriptTypesEvent(Consumer<ScriptType<?>> types, Consumer<TargetApplicability.Type<?>> applicabilities) {
        this.types = types;
        this.applicabilities = applicabilities;
    }

    public void register(ScriptType<?> type) {
        types.accept(type);
    }

    public void registerApplicability(TargetApplicability.Type<?> type) {
        applicabilities.accept(type);
    }
}
