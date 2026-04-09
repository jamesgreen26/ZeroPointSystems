package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

public record ScriptGetter<O>(
        String displayName,
        Class<O> outputType,
        ResourceLocation outputKey,
        Function<ScriptContext, O> function,
        @Nullable Set<ResourceLocation> associatedBlocks
) implements ScriptNode {

    public static <O> ScriptGetter<O> withBlocks(
            String displayName,
            Class<O> outputType,
            ResourceLocation outputKey,
            Function<ScriptContext, O> function,
            Set<ResourceLocation> associatedBlocks
    ) {
        return new ScriptGetter<>(displayName, outputType, outputKey, function, associatedBlocks);
    }
}