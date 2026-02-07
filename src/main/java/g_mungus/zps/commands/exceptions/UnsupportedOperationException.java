package g_mungus.zps.commands.exceptions;

import net.minecraft.network.chat.Component;

public class UnsupportedOperationException extends ScriptException {
    public UnsupportedOperationException(Component arg) {
        super(arg);
    }
}
