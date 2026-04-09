package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ValueOfDispatchers {
    public static final String TERMINAL_LITERAL = "__zps_end";

    private static final Map<ResourceLocation, CommandDispatcher<CommandSourceStack>> DISPATCHERS = new HashMap<>();

    public static void register(ResourceLocation typeKey, CommandDispatcher<CommandSourceStack> dispatcher) {
        DISPATCHERS.put(typeKey, dispatcher);
    }

    public static CommandDispatcher<CommandSourceStack> get(ResourceLocation typeKey) {
        return DISPATCHERS.get(typeKey);
    }
}
