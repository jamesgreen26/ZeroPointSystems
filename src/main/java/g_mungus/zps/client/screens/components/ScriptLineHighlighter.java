package g_mungus.zps.client.screens.components;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.content.TerminalLines;
import g_mungus.zps.commands.preprocess.AddressPreProcessor;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colours a whole script line by line the way the script terminal does: commands, {@code wait} and
 * {@code receive_value} lines, {@code #def}/{@code #var} definitions and comments. The script's
 * pre-processing is made once and kept until the script, its addresses or the scripts change.
 *
 * <p>Used by the script editors and by anything else that shows a script, such as a text display's
 * {@code <munguscript>} text.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptLineHighlighter {

    static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    static final String WAIT = TerminalLines.WAIT;
    static final String RECEIVE_VALUE = TerminalLines.RECEIVE_VALUE;
    static final String DEFINE = "#def";
    static final String DECLARE = "#var";

    private final boolean commandsOnly;
    private final @Nullable Set<ScriptTarget> connectedTargets;
    private boolean expressionMode;
    private boolean assumeVariablesDeclared;

    private @Nullable PreparedScript prepared;
    private final Map<String, List<ScriptSyntaxHighlighter.Span>> highlightCache = new HashMap<>();

    /** A script's pre-processing, and that of the lines above each {@code #def} line, made as needed. */
    public record PreparedScript(String text, Map<String, BlockPos> addresses, ScriptView<SharedSuggestionProvider> view,
                                 CommandPreProcessor.Prepared script, Map<Integer, CommandPreProcessor.Prepared> before) {
    }

    /**
     * @param commandsOnly     every line is a command, as in the script terminal; otherwise only
     *                         lines starting with {@code /} are
     * @param connectedTargets what the script can be aimed at, or null when not known, which allows everything
     */
    public ScriptLineHighlighter(boolean commandsOnly, @Nullable Set<ScriptTarget> connectedTargets) {
        this.commandsOnly = commandsOnly;
        this.connectedTargets = connectedTargets;
    }

    /** Reads every line as one getter to mapper chain instead. */
    public void setExpressionMode(boolean expressionMode) {
        if (this.expressionMode != expressionMode) {
            this.expressionMode = expressionMode;
            this.highlightCache.clear();
        }
    }

    /**
     * Takes every {@code receive_value} variable as declared, for script text shown away from
     * the script it belongs to, where the declarations may not be.
     */
    public void setAssumeVariablesDeclared(boolean assumeVariablesDeclared) {
        if (this.assumeVariablesDeclared != assumeVariablesDeclared) {
            this.assumeVariablesDeclared = assumeVariablesDeclared;
            this.highlightCache.clear();
        }
    }

    public SharedSuggestionProvider source() {
        return ScriptSyntaxHighlighter.source(this.connectedTargets);
    }

    /** The script's pre-processing: its aliases, then its addresses. */
    public PreparedScript prepared(ScriptView<SharedSuggestionProvider> view, String text, Map<String, BlockPos> addresses) {
        PreparedScript cached = this.prepared;
        if (cached != null && cached.text().equals(text) && cached.addresses().equals(addresses) && cached.view() == view) {
            return cached;
        }
        PreparedScript made = new PreparedScript(text, Map.copyOf(addresses), view,
                prepare(view, Arrays.asList(text.split("\n", -1)), addresses), new HashMap<>());
        this.prepared = made;
        this.highlightCache.clear();
        return made;
    }

    /** The pre-processing an {@code #def} line's own expression sees: the definitions above it. */
    public CommandPreProcessor.Prepared preparedBefore(ScriptView<SharedSuggestionProvider> view, String text,
                                                       Map<String, BlockPos> addresses, int lineNumber) {
        PreparedScript script = prepared(view, text, addresses);
        return script.before().computeIfAbsent(lineNumber, line -> {
            List<String> lines = Arrays.asList(script.text().split("\n", -1));
            return prepare(view, lines.subList(0, Math.min(line, lines.size())), script.addresses());
        });
    }

    private CommandPreProcessor.Prepared prepare(ScriptView<SharedSuggestionProvider> view, List<String> lines,
                                                 Map<String, BlockPos> addresses) {
        return CommandPreProcessor.chain(List.of(view.aliases(), new AddressPreProcessor(addresses)))
                .prepare(lines, new PreProcessContext(view.probe(source()), null));
    }

    /**
     * One line's colouring, kept until the script changes since it costs a parse to work out.
     *
     * @param text       the whole script
     * @param line       the line to colour, as it stands in {@code text}
     * @param lineNumber which line of {@code text} it is, or -1 for a line outside any script
     */
    public List<ScriptSyntaxHighlighter.Span> lineSpans(ScriptView<SharedSuggestionProvider> view, String text,
                                                        Map<String, BlockPos> addresses, String line, int lineNumber) {
        PreparedScript script = prepared(view, text, addresses);
        String key = lineNumber + "\n" + line;
        List<ScriptSyntaxHighlighter.Span> cached = this.highlightCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<ScriptSyntaxHighlighter.Span> spans;
        if (this.expressionMode) {
            spans = ScriptSyntaxHighlighter.expressionSpans(line, 0, line, null, source());
        } else if (lineNumber >= 0 && isAliasDefinitionLine(line)) {
            spans = aliasDefinitionSpans(view, text, addresses, line, lineNumber);
        } else if (lineNumber >= 0 && isCommentLine(line)) {
            spans = List.of();
        } else if (this.commandsOnly || line.startsWith("/")) {
            int offset = line.startsWith("/") ? 1 : 0;
            String command = line.substring(offset);
            if (isWaitLine(command)) {
                spans = waitSpans(line, offset);
            } else if (lineNumber >= 0 && isReceiveLine(command)) {
                spans = receiveSpans(script, line, offset, lineNumber, this.assumeVariablesDeclared);
            } else {
                spans = ScriptSyntaxHighlighter.commandSpans(line, script.script().at(lineNumber), source());
            }
        } else {
            spans = List.of();
        }
        this.highlightCache.put(key, spans);
        return spans;
    }

    private static List<ScriptSyntaxHighlighter.Span> waitSpans(String line, int offset) {
        return keywordLineSpans(line, offset, WAIT, validWait(line.substring(offset))
                ? ScriptSyntaxHighlighter.ARGUMENT_STYLE
                : ScriptSyntaxHighlighter.UNPARSED_STYLE);
    }

    private static List<ScriptSyntaxHighlighter.Span> receiveSpans(PreparedScript script, String line, int offset,
                                                                   int lineNumber, boolean assumeDeclared) {
        String name = TerminalLines.argument(line.substring(offset), RECEIVE_VALUE);
        boolean declared = name != null && !name.isEmpty()
                && (assumeDeclared || script.script().at(lineNumber).variables().containsKey(name));
        return keywordLineSpans(line, offset, RECEIVE_VALUE, declared
                ? ScriptSyntaxHighlighter.GETTER_STYLE
                : ScriptSyntaxHighlighter.UNPARSED_STYLE);
    }

    /** {@code #def name = expression}: the keyword and {@code =} plain, the name as a getter, the expression as one. */
    private List<ScriptSyntaxHighlighter.Span> aliasDefinitionSpans(ScriptView<SharedSuggestionProvider> view, String text,
                                                                    Map<String, BlockPos> addresses, String line,
                                                                    int lineNumber) {
        List<ScriptSyntaxHighlighter.Span> spans = new ArrayList<>();
        int nameStart = aliasNameStart(line);
        if (nameStart == -1) {
            return spans;
        }
        int nameEnd = aliasNameEnd(line);
        int equals = line.indexOf('=');
        if (nameEnd != -1) {
            spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(nameStart, nameEnd), ScriptSyntaxHighlighter.GETTER_STYLE));
        } else {
            int end = equals == -1 ? line.length() : equals;
            spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(nameStart, Math.max(nameStart, end)), ScriptSyntaxHighlighter.UNPARSED_STYLE));
        }
        if (equals == -1) {
            int unexpected = equalsSlotStart(line);
            if (unexpected != -1 && unexpected < line.length()) {
                spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(unexpected, line.length()), ScriptSyntaxHighlighter.UNPARSED_STYLE));
            }
            return spans;
        }
        int start = expressionStart(line, line.length());
        if (start < line.length()) {
            spans.addAll(ScriptSyntaxHighlighter.expressionSpans(line.substring(start), start, line,
                    preparedBefore(view, text, addresses, lineNumber), source()));
        }
        return spans;
    }

    // --- reading lines -------------------------------------------------------------------------------

    /** A {@code #def} line, which may stand on any line; the lines below it may use its alias. */
    static boolean isAliasDefinitionLine(String line) {
        return definitionKeyword(line) != null;
    }

    /** A comment: any other {@code #} line, which may stand on any line. */
    static boolean isCommentLine(String line) {
        return line.stripLeading().startsWith("#");
    }

    /** {@code #def} or {@code #var}, when the line starts with one followed by a space or nothing. */
    static @Nullable String definitionKeyword(String line) {
        String stripped = line.stripLeading();
        for (String keyword : List.of(DEFINE, DECLARE)) {
            if (stripped.startsWith(keyword)
                    && (stripped.length() == keyword.length() || Character.isWhitespace(stripped.charAt(keyword.length())))) {
                return keyword;
            }
        }
        return null;
    }

    /** Where the first non-whitespace character at or after {@code from} is. */
    static int skipWhitespace(String text, int from) {
        int at = from;
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    /** The keyword plain as an executor, then its argument in {@code argumentStyle}. */
    static List<ScriptSyntaxHighlighter.Span> keywordLineSpans(String line, int offset, String keyword,
                                                               Style argumentStyle) {
        List<ScriptSyntaxHighlighter.Span> spans = new ArrayList<>();
        int keywordStart = skipWhitespace(line, offset);
        int keywordEnd = keywordStart + keyword.length();
        spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(keywordStart, keywordEnd), ScriptSyntaxHighlighter.EXECUTOR_STYLE));
        int start = skipWhitespace(line, keywordEnd);
        int end = line.stripTrailing().length();
        if (start < end) {
            spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(start, end), argumentStyle));
        }
        return spans;
    }

    static boolean isReceiveLine(String command) {
        return TerminalLines.isReceiveValue(command);
    }

    static boolean isWaitLine(String command) {
        return TerminalLines.isWait(command);
    }

    /** Whether a {@code wait} line asks for a whole number of cycles in range. */
    static boolean validWait(String command) {
        return TerminalLines.waitCycles(command) != null;
    }

    /** Index where the alias name begins, or -1 if the {@code #def} keyword is not yet followed by whitespace. */
    static int aliasNameStart(String line) {
        int cursor = 0;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        String keyword = definitionKeyword(line);
        if (keyword == null) {
            return -1;
        }

        cursor += keyword.length();
        if (cursor >= line.length() || !Character.isWhitespace(line.charAt(cursor))) {
            return -1;
        }
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    /** Index just past the alias name, or -1 if no valid name has been typed yet. */
    static int aliasNameEnd(String line) {
        int nameStart = aliasNameStart(line);
        if (nameStart == -1) {
            return -1;
        }

        Matcher nameMatcher = ALIAS_NAME_PATTERN.matcher(line);
        nameMatcher.region(nameStart, line.length());
        return nameMatcher.lookingAt() ? nameMatcher.end() : -1;
    }

    /**
     * Index where the {@code =} belongs: the first non-whitespace character after a whitespace-terminated
     * alias name, or the end of the line when only whitespace follows. -1 when the name is not terminated.
     */
    static int equalsSlotStart(String line) {
        int nameEnd = aliasNameEnd(line);
        if (nameEnd == -1 || nameEnd >= line.length() || !Character.isWhitespace(line.charAt(nameEnd))) {
            return -1;
        }

        int cursor = nameEnd;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    /** Where the expression of an {@code #def} line starts: past the {@code =} and any spaces up to {@code limit}. */
    static int expressionStart(String line, int limit) {
        int start = line.indexOf('=') + 1;
        while (start < limit && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        return start;
    }
}
