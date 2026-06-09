package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.Objects;
import java.util.function.Predicate;

public class ZPSLiteral<S> extends LiteralCommandNode<S> {
    public ZPSLiteral(String literal, Command<S> command, Predicate<S> requirement, CommandNode<S> redirect, RedirectModifier<S> modifier, boolean forks) {
        super(literal, command, requirement, redirect, modifier, forks);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        if (!(o instanceof CommandNode<?> other)) return false;
        return Objects.equals(this.getRedirect(), other.getRedirect());
    }

    @Override
    public int hashCode() {
        // Hash the redirect by name only: hashing the node itself can recurse infinitely
        // when redirects form cycles, and identity hashing violates the equals/hashCode
        // contract for equal-but-distinct redirect targets.
        CommandNode<S> redirect = getRedirect();
        return Objects.hash(super.hashCode(), redirect == null ? null : redirect.getName());
    }

    public static class Builder<S> extends LiteralArgumentBuilder<S> {
        public Builder(String literal) {
            super(literal);
        }

        @Override
        public Builder<S> redirect(CommandNode<S> target) {
            super.redirect(target);
            return this;
        }

        @Override
        public ZPSLiteral<S> build() {
            final ZPSLiteral<S> result = new ZPSLiteral<>(getLiteral(), getCommand(), getRequirement(), getRedirect(), getRedirectModifier(), isFork());

            for (final CommandNode<S> argument : getArguments()) {
                result.addChild(argument);
            }

            return result;
        }
    }
}
