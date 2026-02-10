package g_mungus.zps.commands.lang.v2;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public record MappedArgumentType<I, O> (
        ArgumentType<I> delegate,
        BiFunction<I, CommandSourceStack, O> mapper,
        Class<I> argumentClass
) {
    public O parse(StringReader stringReader, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        return mapper.apply(delegate.parse(stringReader), commandSourceStack);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return delegate.listSuggestions(context, builder);
    }

    public Collection<String> getExamples() {
        return delegate.getExamples();
    }

    public static <T> MappedArgumentType<T, T> simple(ArgumentType<T> argumentType, Class<T> type) {
        return new MappedArgumentType<>(argumentType, (a, b) -> a, type);
    }
}
