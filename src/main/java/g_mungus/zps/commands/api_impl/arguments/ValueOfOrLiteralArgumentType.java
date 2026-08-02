package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api_impl.TypeKeys;
import g_mungus.zps.commands.api_impl.ValueOfDispatchers;
import g_mungus.zps.commands.api_impl.ZPSScriptCommandSource;
import g_mungus.zps.commands.api_impl.aliases.ScriptAliases;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueOfOrLiteralArgumentType<A> implements ArgumentType<Object> {
    private static final String ARGUMENT_PLACEHOLDER = "%s";
    private static final String VALUE_OF_PREFIX = "value_of(";
    private static final Pattern ADDRESS_TOKEN = Pattern.compile("^@([A-Za-z0-9._]+)");

    private static volatile Set<String> activeAddressNames = Set.of(); // modified clientside only!!!
    private static volatile Set<String> activeExpressionAliasNames = Set.of(); // modified clientside only!!!
    private static volatile Map<String, ScriptAliases.AliasDefinition> activeExpressionAliases = Map.of(); // modified clientside only!!!

    private final ArgumentType<A> wrappedType;
    private final ResourceLocation targetTypeKey;

    private ValueOfOrLiteralArgumentType(ArgumentType<A> wrappedType, ResourceLocation targetTypeKey) {
        this.wrappedType = wrappedType;
        this.targetTypeKey = targetTypeKey;
    }

    public static <A> ValueOfOrLiteralArgumentType<A> of(ArgumentType<A> wrappedType, ResourceLocation targetTypeKey) {
        return new ValueOfOrLiteralArgumentType<>(wrappedType, targetTypeKey);
    }

    public ArgumentType<A> wrappedType() {
        return wrappedType;
    }

    public ResourceLocation targetTypeKey() {
        return targetTypeKey;
    }

    /// Only call this clientside!!
    public static void setActiveAddressNames(Collection<String> addressNames) {
        activeAddressNames = Set.copyOf(addressNames);
    }

    /// Only call this clientside!!
    public static void setActiveExpressionAliasNames(Collection<String> aliasNames) {
        activeExpressionAliasNames = Set.copyOf(aliasNames);
        activeExpressionAliases = Map.of();
    }

    /// Only call this clientside!!
    public static void setActiveExpressionAliases(Map<String, ScriptAliases.AliasDefinition> aliases) {
        activeExpressionAliases = Map.copyOf(aliases);
        activeExpressionAliasNames = Set.copyOf(aliases.keySet());
    }

    public static boolean isActiveExpressionAliasName(String aliasName) {
        return activeExpressionAliasNames.contains(aliasName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <S> CommandDispatcher<S> valueOfDispatcherWithActiveExpressionAliases(ResourceLocation targetTypeKey, S source) {
        return valueOfDispatcherWithExpressionAliases(targetTypeKey, activeExpressionAliases, source);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <S> CommandDispatcher<S> valueOfDispatcherWithExpressionAliases(
            ResourceLocation targetTypeKey,
            Map<String, ScriptAliases.AliasDefinition> aliases,
            S source
    ) {
        CommandDispatcher<S> dispatcher = (CommandDispatcher<S>) ValueOfDispatchers.get(targetTypeKey);
        if (dispatcher == null || aliases.isEmpty()) {
            return dispatcher;
        }

        CommandDispatcher<S> copy = copyDispatcher(dispatcher);
        for (ScriptAliases.AliasDefinition alias : aliases.values()) {
            ResourceLocation outputType = inferAliasOutputType(alias, source);
            if (outputType == null) {
                continue;
            }
            CommandNode<S> aliasDestination = findNode(copy.getRoot(), "have-" + outputType);
            if (aliasDestination != null) {
                copy.getRoot().addChild(
                        LiteralArgumentBuilder.<S>literal(alias.name())
                                .redirect(aliasDestination)
                                .build()
                );
            }
        }
        return copy;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <S> ResourceLocation inferAliasOutputType(ScriptAliases.AliasDefinition alias, S source) {
        String expression = ScriptAliases.resolveExpression(alias.expression(), alias.visibleAliases());
        for (ResourceLocation typeKey : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            var dispatcher = ValueOfDispatchers.get(typeKey);
            if (dispatcher == null) {
                continue;
            }
            try {
                var rawDispatcher = (CommandDispatcher) dispatcher;
                var parse = rawDispatcher.parse(expression + " " + ValueOfDispatchers.TERMINAL_LITERAL, source);
                if (!parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
                    return typeKey;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static <S> CommandDispatcher<S> copyDispatcher(CommandDispatcher<S> original) {
        CommandDispatcher<S> copy = new CommandDispatcher<>();
        Map<CommandNode<S>, CommandNode<S>> copiedNodes = new IdentityHashMap<>();
        copiedNodes.put(original.getRoot(), copy.getRoot());
        for (CommandNode<S> child : original.getRoot().getChildren()) {
            copy.getRoot().addChild(copyNode(child, copiedNodes));
        }
        return copy;
    }

    private static <S> CommandNode<S> copyNode(
            CommandNode<S> original,
            Map<CommandNode<S>, CommandNode<S>> copiedNodes
    ) {
        CommandNode<S> existing = copiedNodes.get(original);
        if (existing != null) {
            return existing;
        }

        ArgumentBuilder<S, ?> builder = original.createBuilder();
        if (original.getRedirect() != null) {
            builder.forward(
                    copyNode(original.getRedirect(), copiedNodes),
                    original.getRedirectModifier(),
                    original.isFork()
            );
        }

        CommandNode<S> copy = builder.build();
        copiedNodes.put(original, copy);
        for (CommandNode<S> child : original.getChildren()) {
            copy.addChild(copyNode(child, copiedNodes));
        }
        return copy;
    }

    @Nullable
    private static <S> CommandNode<S> findNode(CommandNode<S> root, String name) {
        Set<CommandNode<S>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findNode(root, name, visited);
    }

    @Nullable
    private static <S> CommandNode<S> findNode(CommandNode<S> node, String name, Set<CommandNode<S>> visited) {
        if (!visited.add(node)) {
            return null;
        }
        if (node.getName().equals(name)) {
            return node;
        }
        for (CommandNode<S> child : node.getChildren()) {
            CommandNode<S> found = findNode(child, name, visited);
            if (found != null) {
                return found;
            }
        }
        if (node.getRedirect() != null) {
            return findNode(node.getRedirect(), name, visited);
        }
        return null;
    }

    @Override
    public Object parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String remaining = reader.getRemaining();
        if (isBlockPosArgument()) {
            Matcher matcher = ADDRESS_TOKEN.matcher(remaining);
            if (matcher.find() && isPlaceholderBoundary(reader, cursor + matcher.group(0).length())) {
                String name = matcher.group(1);
                if (getAvailableAddressNames(null).contains(name)) {
                    reader.setCursor(cursor + matcher.group(0).length());
                    return new AddressReference(name);
                }
            }
        }
        if (remaining.startsWith(ARGUMENT_PLACEHOLDER) && isPlaceholderBoundary(reader, cursor + ARGUMENT_PLACEHOLDER.length())) {
            reader.setCursor(cursor + ARGUMENT_PLACEHOLDER.length());
            return new ArgumentPlaceholder(targetTypeKey);
        }
        if (remaining.startsWith(VALUE_OF_PREFIX)) {
            reader.setCursor(cursor + VALUE_OF_PREFIX.length());
            int depth = 1;
            int start = reader.getCursor();
            boolean inQuotedString = false;
            boolean escaping = false;
            while (reader.canRead() && depth > 0) {
                char c = reader.read();
                if (inQuotedString) {
                    if (escaping) {
                        escaping = false;
                        continue;
                    }
                    if (c == '\\') {
                        escaping = true;
                        continue;
                    }
                    if (c == '"') {
                        inQuotedString = false;
                    }
                    continue;
                }

                if (c == '"') {
                    inQuotedString = true;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
            }
            if (depth != 0 || inQuotedString || escaping) {
                reader.setCursor(cursor);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
            }
            String inner = reader.getString().substring(start, reader.getCursor() - 1);
            return new ValueOfExpression<>(inner, targetTypeKey);
        }
        return wrappedType.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        var addressSuggestions = CompletableFuture.completedFuture(new SuggestionsBuilder(builder.getInput(), builder.getStart()).build());

        if (!remaining.isBlank()) {

            if (remaining.startsWith(VALUE_OF_PREFIX)) {
                String innerRemaining = remaining.substring(VALUE_OF_PREFIX.length());
                var innerDispatcher = valueOfDispatcherWithActiveExpressionAliases(targetTypeKey, context.getSource());
                if (innerDispatcher != null) {
                    try {
                        @SuppressWarnings("rawtypes")
                        var rawDispatcher = (CommandDispatcher) innerDispatcher;
                        var parseResults = rawDispatcher.parse(innerRemaining, context.getSource());
                        return rawDispatcher.getCompletionSuggestions(parseResults, innerRemaining.length())
                                .thenApply(suggestions -> translateInnerSuggestions(builder, innerRemaining, (Suggestions) suggestions, context.getSource()));
                    } catch (Exception ignored) {
                    }
                }
                return Suggestions.empty();
            }

            if (isBlockPosArgument()) {
                addressSuggestions = CompletableFuture.completedFuture(
                        buildAddressSuggestions(builder, context.getSource(), remaining)
                );
            }
        } else if (isBlockPosArgument()) {
            addressSuggestions = CompletableFuture.completedFuture(
                    buildAddressSuggestions(builder, context.getSource(), remaining)
            );
        }

        SuggestionsBuilder withValueOf = builder.createOffset(builder.getStart());
        if (VALUE_OF_PREFIX.startsWith(remaining) && !remaining.isBlank()) {
            withValueOf.suggest(VALUE_OF_PREFIX);
        }


        return wrappedType.listSuggestions(context, builder)
                .thenCombine(CompletableFuture.completedFuture(withValueOf.buildFuture().join()), (wrappedSuggestions, valueOfSuggestions) -> {
                    var combined = new SuggestionsBuilder(builder.getInput(), builder.getStart());
                    wrappedSuggestions.getList().forEach(s -> combined.suggest(s.getText(), s.getTooltip()));
                    valueOfSuggestions.getList().forEach(s -> combined.suggest(s.getText(), s.getTooltip()));
                    return combined.build();
                })
                .thenCombine(addressSuggestions, (existingSuggestions, addressSuggestionSet) -> {
                    var combined = new SuggestionsBuilder(builder.getInput(), builder.getStart());
                    existingSuggestions.getList().forEach(s -> combined.suggest(s.getText(), s.getTooltip()));
                    addressSuggestionSet.getList().forEach(s -> combined.suggest(s.getText(), s.getTooltip()));
                    return combined.build();
                });
    }

    @Override
    public Collection<String> getExamples() {
        List<String> examples = new ArrayList<>();
        examples.add("value_of(pos x)");
        examples.add("value_of(pos x + 1)");
        for (String wrappedExample : wrappedType.getExamples()) {
            examples.add(wrappedExample);
            if (examples.size() >= 5) {
                break;
            }
        }
        return examples;
    }

    private Suggestions translateInnerSuggestions(
            SuggestionsBuilder builder,
            String innerRemaining,
            Suggestions suggestions,
            Object source
    ) {
        int innerOffset = builder.getStart() + VALUE_OF_PREFIX.length();
        List<Suggestion> translated = new ArrayList<>();
        boolean hasClosingSuggestion = false;

        for (Suggestion suggestion : suggestions.getList()) {
            String text = suggestion.getText();
            if (ValueOfDispatchers.TERMINAL_LITERAL.equals(text)) {
                translated.add(new Suggestion(
                        StringRange.at(innerOffset + innerRemaining.length()),
                        ")",
                        suggestion.getTooltip()
                ));
                hasClosingSuggestion = true;
                continue;
            }

            translated.add(new Suggestion(
                    StringRange.between(
                            suggestion.getRange().getStart() + innerOffset,
                            suggestion.getRange().getEnd() + innerOffset
                    ),
                    text,
                    suggestion.getTooltip()
            ));
        }

        if (!hasClosingSuggestion && canCloseValueOf(innerRemaining, source)) {
            translated.add(new Suggestion(
                    StringRange.at(innerOffset + innerRemaining.length()),
                    ")"
            ));
        }

        return Suggestions.create(builder.getInput(), translated);
    }

    private boolean canCloseValueOf(String innerRemaining, Object source) {
        var innerDispatcher = valueOfDispatcherWithActiveExpressionAliases(targetTypeKey, source);
        if (innerDispatcher == null || innerRemaining.isBlank()) {
            return false;
        }

        @SuppressWarnings("rawtypes")
        var rawDispatcher = (CommandDispatcher) innerDispatcher;
        String completedExpression = innerRemaining.stripTrailing();
        var parseResults = rawDispatcher.parse(completedExpression + " " + ValueOfDispatchers.TERMINAL_LITERAL, source);
        return !parseResults.getReader().canRead() && parseResults.getExceptions().isEmpty();
    }

    private static boolean isPlaceholderBoundary(StringReader reader, int endCursor) {
        if (endCursor >= reader.getTotalLength()) {
            return true;
        }

        return Character.isWhitespace(reader.getString().charAt(endCursor));
    }

    private boolean isBlockPosArgument() {
        return wrappedType instanceof BlockPosArgument;
    }

    private Suggestions buildAddressSuggestions(SuggestionsBuilder builder, Object source, String remaining) {
        SuggestionsBuilder addressBuilder = builder.createOffset(builder.getStart());
        String lowerRemaining = remaining.toLowerCase();
        Collection<String> availableAddresses = getAvailableAddressNames(source);
        for (String address : availableAddresses) {
            String suggestion = "@" + address;
            if (suggestion.toLowerCase().startsWith(lowerRemaining)) {
                addressBuilder.suggest(suggestion);
            }
        }
        return addressBuilder.build();
    }

    private static Collection<String> getAvailableAddressNames(Object source) {
        if (source instanceof ZPSScriptCommandSource scriptSource) {
            return scriptSource.getAvailableAddressNames();
        }
        return activeAddressNames;
    }
}
