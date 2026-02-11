package g_mungus.zps.commands.api;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

public record ScriptExecutor<I, A>(
        String displayName,
        Class<I> inputType,
        ResourceLocation inputKey,
        ArgumentType<A> argumentType,
        Class<A> argumentClass, // new
        BiFunction<A, ScriptContext, I> argumentMapper,
        BiFunction<I, ScriptContext.WithArgument<I>, Integer> function
) implements ScriptNode {
    public static <I> ScriptExecutor<I, I> simple(
            String displayName,
            Class<I> inputType,
            ResourceLocation inputKey,
            ArgumentType<I> argumentType,
            BiFunction<I, ScriptContext.WithArgument<I>, Integer> function
    ) {
        return new ScriptExecutor<>(
                displayName,
                inputType,
                inputKey,
                argumentType,
                inputType,
                (a, b) -> a,
                function
        );
    }
}