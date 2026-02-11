package g_mungus.zps.commands.api;

import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
public final class ScriptMapper2<I, O> extends ScriptMapper<I, O> {

    private final MappedArgumentType<?, I> argumentType;

    public ScriptMapper2(
            String displayName,
            Class<I> inputType,
            Class<O> outputType,
            ResourceLocation inputKey,
            ResourceLocation outputKey,
            BiFunction<I, Context<I>, O> function,
            MappedArgumentType<?, I> argumentType
    ) {
        super(
                displayName,
                inputType,
                outputType,
                inputKey,
                outputKey,
                (a, b) -> {
                    if (b instanceof Context<?> context) {
                        return function.apply(a, (Context<I>) context);
                    } else {
                        throw new RuntimeException("Incorrect use of ScriptMapper2");
                    }
                }
        );
        this.argumentType = argumentType;
    }

    public MappedArgumentType<?, I> argumentType() {
        return argumentType;
    }

    public interface Context<I> extends ScriptContext {
        I otherValue();
    }
}
