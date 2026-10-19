package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.SimpleExecutor;
import g_mungus.zps.commands.phystick.PhysTickProgram;
import net.minecraft.commands.CommandBuildContext;
import net.neoforged.bus.api.Event;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Collects the getters, mappers and executors scripts can use, each time the server builds them.
 * Make nodes with {@link ZPSNodes}; the types they use come from {@link ZPSScriptTypes},
 * {@link g_mungus.munguscript.language.builtin.BuiltInTypes} or a {@link RegisterScriptTypesEvent}.
 */
public class RegisterScriptCommandsEvent extends Event {
    private final Consumer<ScriptNode> registrar;
    private final Consumer<ScriptNode> physTickSafe;
    private final CommandBuildContext buildContext;
    private final Set<String> getterNames = new HashSet<>();

    /** @param physTickSafe given each node registered as safe in a phys tick program, after it is registered */
    public RegisterScriptCommandsEvent(Consumer<ScriptNode> registrar, Consumer<ScriptNode> physTickSafe,
                                       CommandBuildContext buildContext) {
        this.registrar = registrar;
        this.physTickSafe = physTickSafe;
        this.buildContext = buildContext;
    }

    public void register(ScriptNode node) {
        registrar.accept(node);
        if (node instanceof ScriptGetter<?> getter) {
            getterNames.add(getter.displayName());
        }
    }

    /**
     * Registers a getter or mapper that a phys tick program ({@code on_phys_tick}) may run inside a grid's physics
     * step, which may be on a physics thread of its own. It must not touch the level or any block entity but its
     * target's own published state. It may read the step from {@link ZPSScriptContext#physics()}, which is null
     * wherever else it runs.
     * <p>
     * In a program, a chain with a word that is not safe is worked out on the server tick before the step, up to
     * and including the last such word. MungusScript's own mappers are always safe. For an executor, use
     * {@link #registerPhysTickExecutor}.
     */
    public void registerPhysTickSafe(ScriptNode node) {
        register(node);
        physTickSafe.accept(node);
    }

    /**
     * Registers an executor for phys tick programs: safe to run in the physics step, as
     * {@link #registerPhysTickSafe} says, and meant for inside {@code on_phys_tick}, where no other executor
     * parses. Outside a program it is not suggested, and it runs without a step to act on.
     */
    public void registerPhysTickExecutor(SimpleExecutor<?, ?> executor) {
        registerPhysTickSafe(executor.onlyInside(PhysTickProgram.ON_PHYS_TICK));
    }

    /**
     * Whether a getter called {@code name} has been registered so far. Getter names must be unique,
     * so a name made up at runtime, such as from another mod's labels, is checked first.
     */
    public boolean hasGetter(String name) {
        return getterNames.contains(name);
    }

    /** For argument types that read registries, such as items or block predicates. */
    public CommandBuildContext buildContext() {
        return buildContext;
    }
}
