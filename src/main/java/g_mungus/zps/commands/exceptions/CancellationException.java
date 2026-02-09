package g_mungus.zps.commands.exceptions;

import net.minecraft.network.chat.Component;

public class CancellationException extends ScriptException {
    public CancellationException(Component arg) {
        super(arg);
    }
}
