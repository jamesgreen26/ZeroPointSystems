package g_mungus.zps.commands.api;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiFunction;

public record ScriptExecutor<I, A>(
        String displayName,
        Class<I> inputType,
        ResourceLocation inputKey,
        ArgumentType<A> argumentType,
        Class<A> argumentClass, // new
        BiFunction<A, ScriptContext, I> argumentMapper,
        BiFunction<I, ScriptContext, Integer> function,
        @Nullable Set<ResourceLocation> associatedBlocks
    ) implements ScriptNode {

    public static <I> ScriptExecutor<I, I> simple(
            String displayName,
            Class<I> inputType,
            ResourceLocation inputKey,
            ArgumentType<I> argumentType,
            BiFunction<I, ScriptContext, Integer> function
    ) {
        return new ScriptExecutor<>(
                displayName,
                inputType,
                inputKey,
                argumentType,
                inputType,
                (a, b) -> a,
                function,
                null
        );
    }

    public static <I> ScriptExecutor<I, I> simpleWithBlocks(
            String displayName,
            Class<I> inputType,
            ResourceLocation inputKey,
            ArgumentType<I> argumentType,
            BiFunction<I, ScriptContext, Integer> function,
            Set<ResourceLocation> associatedBlocks
    ) {
        return new ScriptExecutor<>(
                displayName,
                inputType,
                inputKey,
                argumentType,
                inputType,
                (a, b) -> a,
                function,
                associatedBlocks
        );
    }
}