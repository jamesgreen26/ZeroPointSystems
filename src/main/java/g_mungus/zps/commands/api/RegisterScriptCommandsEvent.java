package g_mungus.zps.commands.api;

import net.minecraftforge.eventbus.api.Event;

public abstract class RegisterScriptCommandsEvent extends Event {
    public abstract void register(ScriptNode node);
}
