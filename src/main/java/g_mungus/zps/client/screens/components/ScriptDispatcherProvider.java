package g_mungus.zps.client.screens.components;

import com.mojang.brigadier.CommandDispatcher;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;

public class ScriptDispatcherProvider implements CommandDispatcherProvider {
    private final Minecraft minecraft;
    private CommandDispatcher<SharedSuggestionProvider> scriptDispatcher;

    public ScriptDispatcherProvider(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public CommandDispatcher<SharedSuggestionProvider> get() {
        if (scriptDispatcher == null) {
            assert this.minecraft.player != null;
            CommandDispatcher<SharedSuggestionProvider> rootDispatcher = this.minecraft.player.connection.getCommands();
            scriptDispatcher = ZPSCommands.getScriptDispatcher(rootDispatcher);
        }
        return scriptDispatcher;
    }
}
