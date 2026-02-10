package g_mungus.zps.commands.lang.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.BiFunction;

public record MappedArgumentType<I, O>(
        ArgumentType<I> type,
        BiFunction<I, CommandSourceStack, O> mapper,
        Class<I> argumentClass
) {}
