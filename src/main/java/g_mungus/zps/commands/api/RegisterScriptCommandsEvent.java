package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.ScriptNode;
import net.minecraft.commands.CommandBuildContext;
import net.neoforged.bus.api.Event;

import java.util.function.Consumer;

/**
 * Collects the getters, mappers and executors scripts can use, each time the server builds them.
 * Make nodes with {@link ZPSNodes}; the types they use come from {@link ZPSScriptTypes},
 * {@link g_mungus.munguscript.language.builtin.BuiltInTypes} or a {@link RegisterScriptTypesEvent}.
 */
public class RegisterScriptCommandsEvent extends Event {
    private final Consumer<ScriptNode> registrar;
    private final CommandBuildContext buildContext;

    public RegisterScriptCommandsEvent(Consumer<ScriptNode> registrar, CommandBuildContext buildContext) {
        this.registrar = registrar;
        this.buildContext = buildContext;
    }

    public void register(ScriptNode node) {
        registrar.accept(node);
    }

    /** For argument types that read registries, such as items or block predicates. */
    public CommandBuildContext buildContext() {
        return buildContext;
    }
}
