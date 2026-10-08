package g_mungus.zps.client.screens.components;

import com.google.common.collect.Lists;
import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.zps.client.script.ClientScripts;
import g_mungus.zps.client.script.EditorSuggestionSource;
import g_mungus.zps.commands.api.ScriptTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Colouring for ZPS script text. What each word is comes from the script engine
 * ({@link ScriptView#highlight}); this class only decides how each kind of word looks.
 *
 * <p>Shared by the script editors, which hand over a script's pre-processing so aliases and
 * addresses are coloured as written, and by read-only readouts such as the serial bus screen,
 * which just want one finished command coloured.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptSyntaxHighlighter {

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

    /** One finished command coloured, with no script around it. */
    public static List<Span> spans(String command) {
        if (Minecraft.getInstance().getConnection() == null) {
            return List.of(new Span(StringRange.between(0, command.length()), DEFAULT_STYLE));
        }
        return flatten(command, commandSpans(command, null, source(null)));
    }

    /**
     * A command line's words coloured, a leading {@code /} left plain.
     *
     * @param preProcessing the script's, so its aliases and addresses are coloured as written
     */
    public static List<Span> commandSpans(String line, @Nullable CommandPreProcessor.Prepared preProcessing,
                                          SharedSuggestionProvider source) {
        ScriptView<SharedSuggestionProvider> view = ClientScripts.view();
        if (view == null) {
            return List.of(new Span(StringRange.between(0, line.length()), DEFAULT_STYLE));
        }
        int offset = line.startsWith("/") ? 1 : 0;
        List<Highlight> highlights = view.highlight(line.substring(offset), source, preProcessing);
        return spans(line, highlights, offset);
    }

    /** A bare expression coloured, such as a Serial Bus reads or an alias stands for. */
    public static List<Span> expressionSpans(String expression, int offset, String text,
                                             @Nullable CommandPreProcessor.Prepared preProcessing,
                                             SharedSuggestionProvider source) {
        ScriptView<SharedSuggestionProvider> view = ClientScripts.view();
        if (view == null) {
            return List.of();
        }
        return spans(text, view.highlightDefinition(expression, source, preProcessing), offset);
    }

    /** The highlights, moved {@code offset} along; where they leave gaps, nothing. */
    public static List<Span> spans(String text, List<Highlight> highlights, int offset) {
        List<Span> spans = new ArrayList<>();
        for (Highlight highlight : highlights) {
            StringRange range = highlight.range();
            int start = Math.min(range.getStart() + offset, text.length());
            int end = Math.min(range.getEnd() + offset, text.length());
            spans.add(new Span(StringRange.between(start, end), style(highlight.kind())));
        }
        return spans;
    }

    public static Style style(Highlight.Kind kind) {
        return switch (kind) {
            case KEYWORD -> DEFAULT_STYLE;
            case EXECUTOR -> EXECUTOR_STYLE;
            case GETTER, ALIAS -> GETTER_STYLE;
            case MAPPER -> MAPPER_STYLE;
            case ARGUMENT -> ARGUMENT_STYLE;
            case UNPARSED -> UNPARSED_STYLE;
        };
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

    /** Spans laid end to end, gaps in the default style; where they overlap, the earlier one wins. */
    public static List<Span> flatten(String text, List<Span> spans) {
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort((a, b) -> Integer.compare(a.start(), b.start()));
        List<Span> flat = new ArrayList<>();
        int cursor = 0;
        for (Span span : sorted) {
            int start = Math.max(span.start(), cursor);
            int end = Math.min(span.end(), text.length());
            if (end <= start) continue;
            if (start > cursor) {
                flat.add(new Span(StringRange.between(cursor, start), DEFAULT_STYLE));
            }
            flat.add(new Span(StringRange.between(start, end), span.style()));
            cursor = end;
        }
        if (cursor < text.length()) {
            flat.add(new Span(StringRange.between(cursor, text.length()), DEFAULT_STYLE));
        }
        return flat;
    }

    /** Spans clipped to the visible window, ready for the editor to draw. */
    public static FormattedCharSequence sequence(String text, List<Span> spans, int visibleStart, int visibleLength) {
        List<FormattedCharSequence> list = Lists.newArrayList();
        int visibleEnd = visibleStart + visibleLength;
        for (Span span : flatten(text, spans)) {
            int start = Math.max(span.start(), visibleStart);
            int end = Math.min(span.end(), visibleEnd);
            if (end <= start) continue;
            list.add(FormattedCharSequence.forward(text.substring(start, end), span.style()));
        }
        return FormattedCharSequence.composite(list);
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

    /** The client's suggestion source, knowing what a script can be aimed at when that is given. */
    public static SharedSuggestionProvider source(@Nullable Set<ScriptTarget> connectedTargets) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            throw new IllegalStateException("No connection to suggest with");
        }
        return new EditorSuggestionSource(minecraft.getConnection().getSuggestionsProvider(), connectedTargets);
    }
}
