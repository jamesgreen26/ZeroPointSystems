package g_mungus.zps.client.screens.components;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.SharedSuggestionProvider;

public interface CommandDispatcherProvider {
    CommandDispatcher<SharedSuggestionProvider> get();
}
