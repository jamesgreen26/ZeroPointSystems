package g_mungus.zps.client.screens.components;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;

public class ScriptDispatcherProvider implements CommandDispatcherProvider {
    private final Minecraft minecraft;
    private final boolean allowPlainText;
    private CommandDispatcher<SharedSuggestionProvider> scriptDispatcher;

    public ScriptDispatcherProvider(Minecraft minecraft, boolean allowPlainText) {
        this.minecraft = minecraft;
        this.allowPlainText = allowPlainText;
    }

    @Override
    public CommandDispatcher<SharedSuggestionProvider> get() {
        if (scriptDispatcher == null) {
            assert this.minecraft.player != null;
            CommandDispatcher<SharedSuggestionProvider> rootDispatcher = this.minecraft.player.connection.getCommands();
            scriptDispatcher = ZPSCommands.getScriptDispatcher(rootDispatcher);

            if (allowPlainText) {
                ArgumentCommandNode<SharedSuggestionProvider, String> argumentNode
                        = RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("display_text", StringArgumentType.greedyString()).build();

                scriptDispatcher.getRoot().addChild(argumentNode);
            }
        }
        return scriptDispatcher;
    }
}
