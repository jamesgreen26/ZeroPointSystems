package g_mungus.zps.commands.api_impl.exceptions;

import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.network.chat.Component;

public class ScriptException extends CommandRuntimeException {
    public ScriptException(Component arg) {
        super(arg);
    }
}
