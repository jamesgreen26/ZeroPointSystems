package g_mungus.zps.commands.api;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.node.SimpleArgumentMapper;
import g_mungus.munguscript.language.node.SimpleExecutor;
import g_mungus.munguscript.language.node.SimpleGetter;
import g_mungus.munguscript.language.node.SimpleMapper;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * {@link ScriptNodes} with ZPS's context: each function gets a {@link ZPSScriptContext} rather
 * than the engine's own. Restrict a node to some blocks with {@code withApplicability} and a
 * {@link BlockApplicability}.
 */
public final class ZPSNodes {

    private ZPSNodes() {
    }

    public static <O> SimpleGetter<O> getter(String name, ScriptType<O> outputType,
                                             Function<ZPSScriptContext, O> function) {
        return ScriptNodes.getter(name, outputType, context -> function.apply(ZPSScriptContext.of(context)));
    }

    public static <I, O> SimpleMapper<I, O> mapper(String name, ScriptType<I> inputType, ScriptType<O> outputType,
                                                   BiFunction<I, ZPSScriptContext, O> function) {
        return ScriptNodes.mapper(name, inputType, outputType,
                (input, context) -> function.apply(input, ZPSScriptContext.of(context)));
    }

    /** Takes an argument of {@code argumentType}, which may also be written as {@code value_of(...)}. */
    public static <I, O, A> SimpleArgumentMapper<I, O, A> argumentMapper(
            String name, ScriptType<I> inputType, ScriptType<O> outputType, String hint, ScriptType<A> argumentType,
            WithArgument<I, A, O> function) {
        return ScriptNodes.argumentMapper(name, inputType, outputType, hint, argumentType,
                (input, context) -> function.apply(input, context.argumentValue(argumentType.javaClass()),
                        ZPSScriptContext.of(context)));
    }

    /** Takes a plain Brigadier argument, passed on as it parsed. It cannot be a {@code value_of(...)}. */
    public static <I, O, A> SimpleArgumentMapper<I, O, A> rawArgumentMapper(
            String name, ScriptType<I> inputType, ScriptType<O> outputType, String hint,
            ArgumentType<A> argumentType, Class<A> argumentClass, WithArgument<I, A, O> function) {
        return ScriptNodes.rawArgumentMapper(name, inputType, outputType, hint, argumentType, argumentClass,
                (input, context) -> function.apply(input, context.argumentValue(argumentClass),
                        ZPSScriptContext.of(context)));
    }

    /** Takes the input type's own argument. */
    public static <I> SimpleExecutor<I, ?> executor(String name, ScriptType<I> inputType,
                                                    ToIntBiFunction<I, ZPSScriptContext> function) {
        return ScriptNodes.executor(name, inputType,
                (input, context) -> function.applyAsInt(input, ZPSScriptContext.of(context)));
    }

    /** Takes the input type's argument narrowed, such as an int from 0 to 15. */
    public static <I> SimpleExecutor<I, ?> executor(String name, ScriptType<I> inputType, ArgumentType<?> narrowed,
                                                    ToIntBiFunction<I, ZPSScriptContext> function) {
        return ScriptNodes.executor(name, inputType, narrowed,
                (input, context) -> function.applyAsInt(input, ZPSScriptContext.of(context)));
    }

    /** Takes an argument of some other kind, which {@code argumentMapper} turns into the input. */
    public static <I, A> SimpleExecutor<I, A> executor(String name, ScriptType<I> inputType,
                                                       ArgumentType<A> argumentType, Class<A> argumentClass,
                                                       BiFunction<A, ZPSScriptContext, I> argumentMapper,
                                                       ToIntBiFunction<I, ZPSScriptContext> function) {
        return ScriptNodes.executor(name, inputType, argumentType, argumentClass,
                (argument, context) -> argumentMapper.apply(argument, ZPSScriptContext.of(context)),
                (input, context) -> function.applyAsInt(input, ZPSScriptContext.of(context)));
    }

    /** A mapper's function, given the value, the argument written after the mapper, and the context. */
    @FunctionalInterface
    public interface WithArgument<I, A, O> {
        O apply(I input, A argument, ZPSScriptContext context);
    }
}
