package g_mungus.zps.commands.api;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
public final class ScriptMapper2<I, O, A> extends ScriptMapper<I, O> {

    private final ArgumentType<A> argumentType;
    private final Class<A> argumentClass;

    public ScriptMapper2(
            String displayName,
            Class<I> inputType,
            Class<O> outputType,
            ResourceLocation inputKey,
            ResourceLocation outputKey,
            BiFunction<I, ScriptContext.WithArgument<A>, O> function,
            ArgumentType<A> argumentType,
            Class<A> argumentClass
    ) {
        super(
                displayName,
                inputType,
                outputType,
                inputKey,
                outputKey,
                (a, b) -> {
                    if (b instanceof ScriptContext.WithArgument<?> context) {
                        return function.apply(a, (ScriptContext.WithArgument<A>) context);
                    } else {
                        throw new RuntimeException("Incorrect use of ScriptMapper2");
                    }
                }
        );
        this.argumentType = argumentType;
        this.argumentClass = argumentClass;
    }

    public ArgumentType<A> argumentType() {
        return argumentType;
    }

    public Class<A> argumentClass() {
        return argumentClass;
    }
}
