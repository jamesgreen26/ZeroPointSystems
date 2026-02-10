package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public record ScriptGetter<O>(
        Class<O> outputType,
        ResourceLocation key,
        ResourceLocation outputKey,
        Function<ScriptContext, O> function
) implements ScriptNode { }