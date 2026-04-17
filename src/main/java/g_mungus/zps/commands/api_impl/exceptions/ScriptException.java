package g_mungus.zps.commands.api_impl.exceptions;

import net.minecraft.network.chat.Component;

public class ScriptException extends RuntimeException {
    public ScriptException(Component arg) {
        super(arg.getString());
    }
}
