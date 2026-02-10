package g_mungus.zps.commands.api_impl.exceptions;

import net.minecraft.network.chat.Component;

public class CancellationException extends ScriptException {
    public CancellationException(Component arg) {
        super(arg);
    }
}
