package g_mungus.zps.commands.api;

import net.minecraft.commands.CommandBuildContext;
import net.neoforged.bus.api.Event;

import java.util.function.Consumer;

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

    public CommandBuildContext buildContext() {
        return buildContext;
    }
}
