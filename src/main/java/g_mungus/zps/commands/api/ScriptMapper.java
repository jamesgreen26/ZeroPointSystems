package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

public record ScriptMapper<I, O>(
        String displayName,
        Class<I> inputType,
        Class<O> outputType,
        ResourceLocation inputKey,
        ResourceLocation outputKey,
        BiFunction<I, ScriptContext, O> function
) implements ScriptNode { }
