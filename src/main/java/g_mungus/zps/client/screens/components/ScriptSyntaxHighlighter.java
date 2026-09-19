package g_mungus.zps.client.screens.components;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api_impl.TypeKeys;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.aliases.ScriptAliases;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colouring for ZPS script text: which parts of a command are executors, getters, mappers,
 * arguments, or simply do not parse.
 *
 * <p>Shared by the script terminal's editor — where the text is live and each line carries its own
 * alias context — and by read-only readouts such as the serial bus screen, which just want a
 * coloured {@link Component} for one finished command. The terminal owns the parsing (it knows
 * about lines, aliases, and the cursor) and hands the results here; everything about how the
 * result is coloured lives in this class.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptSyntaxHighlighter {

    private static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String ARGUMENT_PLACEHOLDER = "%s";

    public static final int EXECUTOR_COLOR = 0xF5A97F;
    public static final int GETTER_COLOR = 0xC792EA;

    public static final Style UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    public static final Style EXECUTOR_STYLE = Style.EMPTY.withColor(EXECUTOR_COLOR);
    public static final Style GETTER_STYLE = Style.EMPTY.withColor(GETTER_COLOR);
    public static final Style MAPPER_STYLE = Style.EMPTY.withColor(0x4C99C9);
    public static final Style ARGUMENT_STYLE = Style.EMPTY.withColor(0x79F1A3);
    public static final Style DEFAULT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);

    private ScriptSyntaxHighlighter() {
    }

    /** A run of text and the style it is drawn in. */
    public record Span(StringRange range, Style style) {
        public int start() {
            return range.getStart();
        }

        public int end() {
            return range.getEnd();
        }
    }

    // --- Entry points -------------------------------------------------------------------------

    /**
     * The whole command coloured, for callers that hold no parse of their own. Parsing is done
     * against the client's script dispatcher with no alias context, so this suits single finished
     * commands rather than a multi-line script.
     */
    public static List<Span> spans(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of(new Span(StringRange.between(0, command.length()), DEFAULT_STYLE));
        }
        try {
            StringReader reader = new StringReader(command);
            if (reader.canRead() && reader.peek() == '/') {
                reader.skip();
            }
            SharedSuggestionProvider source = minecraft.player.connection.getSuggestionsProvider();
            ParseResults<SharedSuggestionProvider> parse =
                    new ScriptDispatcherProvider(minecraft).get().parse(reader, source);
            return flatten(command, commandSpans(parse, Map.of()), parse);
        } catch (Exception e) {
            return List.of(new Span(StringRange.between(0, command.length()), DEFAULT_STYLE));
        }
    }

    /** The whole command coloured as a component. */
    public static Component highlight(String command) {
        return component(command, spans(command), 0, command.length());
    }

    /** The window {@code [from, to)} of {@code text} coloured by the given spans. */
    public static Component component(String text, List<Span> spans, int from, int to) {
        MutableComponent out = Component.empty();
        for (Span span : spans) {
            int start = Math.max(span.start(), from);
            int end = Math.min(span.end(), to);
            if (end <= start) continue;
            out.append(Component.literal(text.substring(start, end)).withStyle(span.style()));
        }
        return out;
    }

    /** The command coloured for the editor, clipped to the line's visible window. */
    public static FormattedCharSequence formatCommand(
            ParseResults<SharedSuggestionProvider> parseResults,
            String fullText,
            int visibleStart,
            int visibleLength,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        List<Span> spans = flatten(fullText, commandSpans(parseResults, expressionAliases), parseResults);
        return sequence(fullText, spans, visibleStart, visibleLength);
    }

    /**
     * A bare expression of a known type coloured for the editor, clipped to the visible window.
     * With the type fixed there is no guessing: anything that does not read as a chain yielding
     * that type keeps the unparsed colour.
     */
    public static FormattedCharSequence formatExpression(
            String fullLine,
            int visibleStart,
            int visibleLength,
            ResourceLocation typeKey,
            SharedSuggestionProvider source
    ) {
        List<Span> spans = expressionSpans(fullLine, typeKey, source);
        return sequence(fullLine, flatten(fullLine, spans, null), visibleStart, visibleLength);
    }

    /** The spans of a bare expression that has to yield {@code typeKey}. */
    public static List<Span> expressionSpans(String expression, ResourceLocation typeKey, SharedSuggestionProvider source) {
        List<Span> spans = new ArrayList<>();
        collectExpressionSpans(expression, 0, spans, Map.of(), source, typeKey);
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    /** An {@code #def} line coloured for the editor, clipped to the line's visible window. */
    public static FormattedCharSequence formatAliasDefinition(
            String fullLine,
            int visibleStart,
            int visibleLength,
            Map<String, ScriptAliases.AliasDefinition> visibleAliases,
            SharedSuggestionProvider source
    ) {
        List<Span> spans = new ArrayList<>();
        collectAliasDefinitionSpans(fullLine, spans, visibleAliases, source);
        spans.sort(Comparator.comparingInt(Span::start));
        return sequence(fullLine, flatten(fullLine, spans, null), visibleStart, visibleLength);
    }

    /** Whether {@code %s} stands alone at {@code start}, waiting to be filled in. */
    public static boolean isArgumentPlaceholderAt(String input, int start) {
        int end = start + ARGUMENT_PLACEHOLDER.length();
        if (start < 0 || end > input.length() || !input.startsWith(ARGUMENT_PLACEHOLDER, start)) {
            return false;
        }

        boolean startsAtBoundary = start == 0 || Character.isWhitespace(input.charAt(start - 1));
        boolean endsAtBoundary = end == input.length() || Character.isWhitespace(input.charAt(end));
        return startsAtBoundary && endsAtBoundary;
    }

    // --- Spans --------------------------------------------------------------------------------

    /** The literal and argument spans of a parsed command, in order and possibly overlapping. */
    private static List<Span> commandSpans(
            ParseResults<SharedSuggestionProvider> parseResults,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        List<Span> spans = new ArrayList<>();
        Set<String> expressionAliasNames = expressionAliases.keySet();
        Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles = buildLiteralStyleIndex(
                parseResults.getContext().getRootNode(),
                expressionAliasNames
        );
        collectLiteralSpans(parseResults.getContext(), spans, literalStyles, expressionAliasNames);
        collectArgumentSpans(parseResults.getContext(), spans, parseResults.getContext().getSource(), expressionAliases);
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    /**
     * The spans laid end to end over the whole text: gaps and leftovers fall back to
     * {@link #DEFAULT_STYLE}, overlaps go to whichever span started first, and anything the parse
     * could not read is marked unparsed.
     */
    private static List<Span> flatten(String text, List<Span> spans, @Nullable ParseResults<?> parseResults) {
        List<Span> flat = new ArrayList<>();
        int cursor = 0;
        for (Span span : spans) {
            int start = Math.max(span.start(), cursor);
            int end = Math.min(span.end(), text.length());
            if (end <= start) continue;
            if (start > cursor) {
                flat.add(new Span(StringRange.between(cursor, start), DEFAULT_STYLE));
            }
            flat.add(new Span(StringRange.between(start, end), span.style()));
            cursor = end;
        }

        if (parseResults != null && parseResults.getReader().canRead()) {
            int fault = parseResults.getReader().getCursor();
            if (fault >= cursor && fault < text.length()) {
                if (fault > cursor) {
                    flat.add(new Span(StringRange.between(cursor, fault), DEFAULT_STYLE));
                }
                boolean placeholder = isArgumentPlaceholderAt(text, fault);
                int end = Math.min(text.length(), fault + (placeholder
                        ? ARGUMENT_PLACEHOLDER.length()
                        : parseResults.getReader().getRemainingLength()));
                flat.add(new Span(StringRange.between(fault, end), placeholder ? ARGUMENT_STYLE : UNPARSED_STYLE));
                cursor = end;
            }
        }

        if (cursor < text.length()) {
            flat.add(new Span(StringRange.between(cursor, text.length()), DEFAULT_STYLE));
        }
        return flat;
    }

    /** Flattened spans clipped to the visible window, ready for the editor to draw. */
    private static FormattedCharSequence sequence(String text, List<Span> flat, int visibleStart, int visibleLength) {
        List<FormattedCharSequence> list = Lists.newArrayList();
        int visibleEnd = visibleStart + visibleLength;
        for (Span span : flat) {
            int start = Math.max(span.start(), visibleStart);
            int end = Math.min(span.end(), visibleEnd);
            if (end <= start) continue;
            list.add(FormattedCharSequence.forward(text.substring(start, end), span.style()));
        }
        return FormattedCharSequence.composite(list);
    }

    private static void collectAliasDefinitionSpans(
            String fullLine,
            List<Span> spans,
            Map<String, ScriptAliases.AliasDefinition> visibleAliases,
            SharedSuggestionProvider source
    ) {
        int cursor = 0;
        while (cursor < fullLine.length() && Character.isWhitespace(fullLine.charAt(cursor))) {
            cursor++;
        }

        int keywordStart = cursor;
        if (!fullLine.startsWith("#def", cursor)) {
            spans.add(new Span(StringRange.between(0, fullLine.length()), UNPARSED_STYLE));
            return;
        }

        int keywordEnd = cursor + "#def".length();
        spans.add(new Span(StringRange.between(keywordStart, keywordEnd), DEFAULT_STYLE));
        cursor = keywordEnd;

        while (cursor < fullLine.length() && Character.isWhitespace(fullLine.charAt(cursor))) {
            cursor++;
        }

        Matcher nameMatcher = ALIAS_NAME_PATTERN.matcher(fullLine);
        nameMatcher.region(cursor, fullLine.length());
        if (nameMatcher.lookingAt()) {
            String name = nameMatcher.group();
            Style nameStyle = ZPSCommands.isMapperName(name) ? UNPARSED_STYLE : GETTER_STYLE;
            spans.add(new Span(StringRange.between(nameMatcher.start(), nameMatcher.end()), nameStyle));
            cursor = nameMatcher.end();
        } else if (cursor < fullLine.length()) {
            int invalidStart = cursor;
            while (cursor < fullLine.length() && !Character.isWhitespace(fullLine.charAt(cursor)) && fullLine.charAt(cursor) != '=') {
                cursor++;
            }
            spans.add(new Span(StringRange.between(invalidStart, cursor), UNPARSED_STYLE));
        }

        int equals = fullLine.indexOf('=', cursor);
        if (equals == -1) {
            int unexpectedStart = cursor;
            while (unexpectedStart < fullLine.length() && Character.isWhitespace(fullLine.charAt(unexpectedStart))) {
                unexpectedStart++;
            }
            if (unexpectedStart < fullLine.length()) {
                spans.add(new Span(StringRange.between(unexpectedStart, fullLine.length()), UNPARSED_STYLE));
            }
            return;
        }

        spans.add(new Span(StringRange.between(equals, equals + 1), DEFAULT_STYLE));
        int expressionStart = equals + 1;
        while (expressionStart < fullLine.length() && Character.isWhitespace(fullLine.charAt(expressionStart))) {
            expressionStart++;
        }
        if (expressionStart < fullLine.length()) {
            collectExpressionSpans(fullLine.substring(expressionStart), expressionStart, spans, visibleAliases, source, null);
        }
    }

    /**
     * @param fixedTypeKey the type the expression must yield, or null to take whichever type reads
     *                     it best — which is all an alias definition can do, as it declares none
     */
    private static void collectExpressionSpans(
            String expression,
            int offset,
            List<Span> spans,
            Map<String, ScriptAliases.AliasDefinition> visibleAliases,
            SharedSuggestionProvider source,
            @Nullable ResourceLocation fixedTypeKey
    ) {
        ParseResults<SharedSuggestionProvider> bestParse = null;
        int bestScore = -1;
        Set<ResourceLocation> candidates = fixedTypeKey != null
                ? Set.of(fixedTypeKey)
                : TypeKeys.TYPE_KEY_TO_CLASS.keySet();
        for (ResourceLocation typeKey : candidates) {
            var dispatcher = ValueOfOrLiteralArgumentType.valueOfDispatcherWithExpressionAliases(
                    typeKey,
                    visibleAliases,
                    source
            );
            if (dispatcher == null) {
                continue;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                var rawDispatcher = (CommandDispatcher) dispatcher;
                ParseResults<SharedSuggestionProvider> parse = rawDispatcher.parse(expression, source);
                int score = parse.getReader().getCursor() * 10 - parse.getExceptions().size();
                if (score > bestScore) {
                    bestScore = score;
                    bestParse = parse;
                }
            } catch (Exception ignored) {
            }
        }

        if (bestParse == null) {
            spans.add(new Span(StringRange.between(offset, offset + expression.length()), ARGUMENT_STYLE));
            return;
        }

        Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles = buildLiteralStyleIndex(
                bestParse.getContext().getRootNode(),
                visibleAliases.keySet()
        );
        collectLiteralSpansWithOffset(bestParse.getContext(), spans, offset, literalStyles, visibleAliases.keySet());
        collectArgumentSpansWithOffset(bestParse.getContext(), spans, bestParse.getContext().getSource(), offset, visibleAliases);

        if (bestParse.getReader().canRead()) {
            int start = offset + bestParse.getReader().getCursor();
            spans.add(new Span(StringRange.between(start, offset + expression.length()), UNPARSED_STYLE));
        }
    }

    private static void collectLiteralSpans(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<Span> spans,
            Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles,
            Set<String> expressionAliasNames
    ) {
        for (ParsedCommandNode<SharedSuggestionProvider> node : context.getNodes()) {
            if (node.getNode() instanceof LiteralCommandNode<?> literalNode) {
                Style style = literalStyles.getOrDefault(node.getNode(), styleForLiteralFallback(literalNode.getLiteral(), expressionAliasNames));
                spans.add(new Span(node.getRange(), style));
            }
        }

        if (context.getChild() != null) {
            collectLiteralSpans(context.getChild(), spans, literalStyles, expressionAliasNames);
        }
    }

    private static void collectArgumentSpans(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<Span> spans,
            SharedSuggestionProvider source,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        for (ParsedArgument<SharedSuggestionProvider, ?> parsedArgument : context.getArguments().values()) {
            Object result = parsedArgument.getResult();
            if (result instanceof ValueOfExpression<?> expr) {
                collectValueOfSpans(expr, parsedArgument.getRange(), spans, source, expressionAliases);
            } else {
                spans.add(new Span(parsedArgument.getRange(), ARGUMENT_STYLE));
            }
        }

        if (context.getChild() != null) {
            collectArgumentSpans(context.getChild(), spans, source, expressionAliases);
        }
    }

    private static void collectValueOfSpans(
            ValueOfExpression<?> expression,
            StringRange range,
            List<Span> spans,
            SharedSuggestionProvider source,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        int start = range.getStart();
        int prefixEnd = Math.min(start + "value_of(".length(), range.getEnd());
        spans.add(new Span(StringRange.between(start, prefixEnd), ARGUMENT_STYLE));

        var innerDispatcher = ValueOfOrLiteralArgumentType.valueOfDispatcherWithExpressionAliases(
                expression.targetTypeKey(),
                expressionAliases,
                source
        );
        if (innerDispatcher != null) {
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                var rawDispatcher = (CommandDispatcher) innerDispatcher;
                ParseResults<SharedSuggestionProvider> innerParse = rawDispatcher.parse(expression.innerExpression(), source);
                Map<CommandNode<SharedSuggestionProvider>, Style> innerLiteralStyles = buildLiteralStyleIndex(
                        innerParse.getContext().getRootNode(),
                        expressionAliases.keySet()
                );
                collectLiteralSpansWithOffset(innerParse.getContext(), spans, prefixEnd, innerLiteralStyles, expressionAliases.keySet());
                collectArgumentSpansWithOffset(innerParse.getContext(), spans, source, prefixEnd, expressionAliases);
            } catch (Exception ignored) {
                spans.add(new Span(StringRange.between(prefixEnd, range.getEnd() - 1), ARGUMENT_STYLE));
            }
        } else if (prefixEnd < range.getEnd() - 1) {
            spans.add(new Span(StringRange.between(prefixEnd, range.getEnd() - 1), ARGUMENT_STYLE));
        }

        if (range.getEnd() > prefixEnd) {
            spans.add(new Span(StringRange.between(range.getEnd() - 1, range.getEnd()), ARGUMENT_STYLE));
        }
    }

    private static void collectLiteralSpansWithOffset(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<Span> spans,
            int offset,
            Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles,
            Set<String> expressionAliasNames
    ) {
        for (ParsedCommandNode<SharedSuggestionProvider> node : context.getNodes()) {
            if (node.getNode() instanceof LiteralCommandNode<?> literalNode) {
                spans.add(new Span(
                        StringRange.between(node.getRange().getStart() + offset, node.getRange().getEnd() + offset),
                        literalStyles.getOrDefault(node.getNode(), styleForLiteralFallback(literalNode.getLiteral(), expressionAliasNames))
                ));
            }
        }

        if (context.getChild() != null) {
            collectLiteralSpansWithOffset(context.getChild(), spans, offset, literalStyles, expressionAliasNames);
        }
    }

    private static void collectArgumentSpansWithOffset(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<Span> spans,
            SharedSuggestionProvider source,
            int offset,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        for (ParsedArgument<SharedSuggestionProvider, ?> parsedArgument : context.getArguments().values()) {
            Object result = parsedArgument.getResult();
            StringRange shiftedRange = StringRange.between(parsedArgument.getRange().getStart() + offset, parsedArgument.getRange().getEnd() + offset);
            if (result instanceof ValueOfExpression<?> expr) {
                collectValueOfSpans(expr, shiftedRange, spans, source, expressionAliases);
            } else {
                spans.add(new Span(shiftedRange, ARGUMENT_STYLE));
            }
        }

        if (context.getChild() != null) {
            collectArgumentSpansWithOffset(context.getChild(), spans, source, offset, expressionAliases);
        }
    }

    private static Map<CommandNode<SharedSuggestionProvider>, Style> buildLiteralStyleIndex(
            CommandNode<SharedSuggestionProvider> root,
            Set<String> expressionAliasNames
    ) {
        Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles = new IdentityHashMap<>();
        Set<CommandNode<SharedSuggestionProvider>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        indexLiteralStyles(root, null, literalStyles, visited, expressionAliasNames);
        return literalStyles;
    }

    private static void indexLiteralStyles(
            CommandNode<SharedSuggestionProvider> node,
            @Nullable String parentLiteral,
            Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles,
            Set<CommandNode<SharedSuggestionProvider>> visited,
            Set<String> expressionAliasNames
    ) {
        if (!visited.add(node)) {
            return;
        }

        String currentLiteral = parentLiteral;
        if (node instanceof LiteralCommandNode<?> literalNode) {
            currentLiteral = literalNode.getLiteral();
            literalStyles.put(node, styleForLiteralNode(currentLiteral, parentLiteral, expressionAliasNames));
        }

        for (CommandNode<SharedSuggestionProvider> child : node.getChildren()) {
            indexLiteralStyles(child, currentLiteral, literalStyles, visited, expressionAliasNames);
        }

        if (node.getRedirect() != null) {
            indexLiteralStyles(node.getRedirect(), currentLiteral, literalStyles, visited, expressionAliasNames);
        }
    }

    private static Style styleForLiteralNode(String literal, @Nullable String parentLiteral, Set<String> expressionAliasNames) {
        if (ZPSCommands.getGetter(literal) != null && (parentLiteral == null || parentLiteral.startsWith("need-"))) {
            return GETTER_STYLE;
        }

        if (expressionAliasNames.contains(literal) || ValueOfOrLiteralArgumentType.isActiveExpressionAliasName(literal)) {
            return GETTER_STYLE;
        }

        if (ZPSCommands.getMapper(literal) != null && parentLiteral != null && parentLiteral.startsWith("have-")) {
            return MAPPER_STYLE;
        }

        if (ZPSCommands.getExecutor(literal) != null && (
                parentLiteral == null
                        || expressionAliasNames.contains(parentLiteral)
                        || parentLiteral.startsWith("have-")
                        || "else".equals(parentLiteral)
                        || ZPSCommands.Paths.EXECUTORS.equals(parentLiteral)
        )) {
            return EXECUTOR_STYLE;
        }

        return DEFAULT_STYLE;
    }

    private static Style styleForLiteralFallback(String literal, Set<String> expressionAliasNames) {
        ScriptExecutor<?, ?> executor = ZPSCommands.getExecutor(literal);
        if (executor != null) {
            return EXECUTOR_STYLE;
        }

        ScriptGetter<?> getter = ZPSCommands.getGetter(literal);
        if (getter != null) {
            return GETTER_STYLE;
        }

        if (expressionAliasNames.contains(literal) || ValueOfOrLiteralArgumentType.isActiveExpressionAliasName(literal)) {
            return GETTER_STYLE;
        }

        ScriptMapper<?, ?> mapper = ZPSCommands.getMapper(literal);
        if (mapper != null) {
            return MAPPER_STYLE;
        }

        return DEFAULT_STYLE;
    }
}
