package g_mungus.zps.commands.lang.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandSourceStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public final class ArgumentTypeRegistry {

    private static final Map<Class<?>, MappedArgumentType<?, ?>> TYPES = new HashMap<>();

    public static <T> void register(Class<T> type, ArgumentType<T> argument) {
        TYPES.put(type, new MappedArgumentType<>(argument, (a, b) -> a, type));
    }

    public static <T, D> void registerMapped(Class<T> type, Class<D> argumentType, ArgumentType<D> argument, BiFunction<D, CommandSourceStack, T> mapper) {
        TYPES.put(type, new MappedArgumentType<>(argument, mapper, argumentType));
    }

    @SuppressWarnings("unchecked")
    public static <T> MappedArgumentType<?, T> get(Class<T> type) {
        return (MappedArgumentType<?, T>) TYPES.get(type);
    }
}
