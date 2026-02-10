package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

public record ScriptExecutor<I>(
        Class<I> inputType,
        ResourceLocation inputKey,
        BiFunction<I, ScriptContext, Integer> function
) implements ScriptNode { }