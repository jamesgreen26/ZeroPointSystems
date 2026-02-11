package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A specialized ArgumentCommandNode that uses redirect-aware equality.
 *
 * <p>Unlike the default {@link ArgumentCommandNode#equals} which only compares argument names,
 * this class also considers redirect targets when determining equality. This prevents incorrect
 * deduplication of argument nodes that have the same name but point to different redirect targets.
 *
 * @param <S> the command source type
 * @param <T> the argument type
 */
public class ZPSArgument<S, T> extends ArgumentCommandNode<S, T> {
    public ZPSArgument(String name, ArgumentType<T> type, Command<S> command, Predicate<S> requirement,
                       CommandNode<S> redirect, RedirectModifier<S> modifier, boolean forks,
                       SuggestionProvider<S> customSuggestions) {
        super(name, type, command, requirement, redirect, modifier, forks, customSuggestions);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        if (!(o instanceof CommandNode<?> other)) return false;
        return Objects.equals(this.getRedirect(), other.getRedirect());
    }

    @Override
    public int hashCode() {
        // Use identityHashCode to avoid infinite recursion when redirect points to parent nodes
        return Objects.hash(super.hashCode(), System.identityHashCode(getRedirect()));
    }

    /**
     * Builder for {@link ZPSArgument}, analogous to {@link com.mojang.brigadier.builder.RequiredArgumentBuilder}.
     *
     * <p>This class extends {@link ArgumentBuilder} directly and copies the functionality of
     * RequiredArgumentBuilder, since that class has a private constructor that prevents extension.
     *
     * <p>Create instances using the static factory method {@link #argument(String, ArgumentType)}.
     *
     * @param <S> the command source type
     * @param <T> the argument type
     */
    public static class Builder<S, T> extends ArgumentBuilder<S, Builder<S, T>> {
        private final String name;
        private final ArgumentType<T> type;
        private SuggestionProvider<S> suggestionsProvider = null;

        public Builder(String name, ArgumentType<T> type) {
            this.name = name;
            this.type = type;
        }

        /**
         * Creates a new ZPSArgument builder.
         *
         * @param name the argument name
         * @param type the argument type
         * @param <S> the command source type
         * @param <T> the argument type
         * @return a new builder instance
         */
        public static <S, T> Builder<S, T> argument(String name, ArgumentType<T> type) {
            return new Builder<>(name, type);
        }

        public Builder<S, T> suggests(SuggestionProvider<S> provider) {
            this.suggestionsProvider = provider;
            return getThis();
        }

        public SuggestionProvider<S> getSuggestionsProvider() {
            return suggestionsProvider;
        }

        @Override
        protected Builder<S, T> getThis() {
            return this;
        }

        public ArgumentType<T> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public ZPSArgument<S, T> build() {
            final ZPSArgument<S, T> result = new ZPSArgument<>(
                    getName(),
                    getType(),
                    getCommand(),
                    getRequirement(),
                    getRedirect(),
                    getRedirectModifier(),
                    isFork(),
                    getSuggestionsProvider()
            );

            for (final CommandNode<S> argument : getArguments()) {
                result.addChild(argument);
            }

            return result;
        }
    }
}
