package g_mungus.zps.client.screens.components;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.zps.client.script.ClientScripts;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.preprocess.AddressPreProcessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestions, usage hints and highlighting for a multi-line script editor, from the scripts the
 * server sent ({@link ClientScripts}). Each line is a command, except for {@code #def} alias
 * definitions, {@code #} comments and {@code wait} lines, which the editor reads itself; all may
 * stand on any line, and a command uses only the aliases defined above it.
 */
@OnlyIn(Dist.CLIENT)
public class MultiLineCommandSuggestions {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");
    private static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NAMED_ARGUMENT_USAGE = Pattern.compile("<[^<>]*:([^<>:]+)>");
    private static final String ALIAS_NAME_USAGE = "<alias>";
    private static final String EXPECTED_EQUALS_MESSAGE = "Expected '=' after alias name";
    private static final String WAIT = "wait";
    private static final String WAIT_USAGE = "<ticks: 1-64>";
    private static final int MAX_WAIT = 64;
    final Minecraft minecraft;
    private final Screen screen;
    final MultiLineEditBox input;
    final Font font;
    private final boolean commandsOnly;
    private final boolean onlyShowIfCursorPastError;
    final int lineStartOffset;
    final int suggestionLineLimit;
    final boolean anchorToBottom;
    final int fillColor;
    private final List<FormattedCharSequence> commandUsage = Lists.<FormattedCharSequence>newArrayList();
    private final @Nullable Set<ScriptTarget> connectedTargets;
    private final Supplier<Map<String, BlockPos>> addresses;
    private int commandUsagePosition;
    private int commandUsageWidth;
    /** The current line as it was parsed: after pre-processing, so it reads differently from the box. */
    private @Nullable ParseResults<SharedSuggestionProvider> currentParse;
    private SourceMap currentMap = SourceMap.IDENTITY;
    /** Where the command starts in the line: past a leading {@code /}. */
    private int currentOffset;
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
    private @Nullable MultiLineCommandSuggestions.SuggestionsList suggestions;
    /** When set, the box holds one bare getter to mapper chain, not a script command. */
    private boolean expressionMode;
    private boolean allowSuggestions;
    boolean keepSuggestions;

    // The script's pre-processing, made again when the script, its addresses or the scripts change.
    private @Nullable PreparedScript prepared;
    private final Map<String, List<ScriptSyntaxHighlighter.Span>> highlightCache = new HashMap<>();

    private record PreparedScript(String text, Map<String, BlockPos> addresses, ScriptView<SharedSuggestionProvider> view,
                                  CommandPreProcessor.Prepared script, Map<Integer, CommandPreProcessor.Prepared> before) {
    }

    /**
     * @param connectedTargets what a script written here can be aimed at, or null when not known,
     *                         which offers everything
     * @param addresses       the addresses a script here can write as {@code @name}
     */
    public MultiLineCommandSuggestions(Minecraft minecraft, Screen screen, MultiLineEditBox input, Font font,
                                       boolean commandsOnly, boolean onlyShowIfCursorPastError, int lineStartOffset,
                                       int suggestionLineLimit, boolean anchorToBottom, int fillColor,
                                       @Nullable Set<ScriptTarget> connectedTargets,
                                       Supplier<Map<String, BlockPos>> addresses) {
        this.connectedTargets = connectedTargets;
        this.addresses = addresses;
        this.minecraft = minecraft;
        this.screen = screen;
        this.input = input;
        this.font = font;
        this.commandsOnly = commandsOnly;
        this.onlyShowIfCursorPastError = onlyShowIfCursorPastError;
        this.lineStartOffset = lineStartOffset;
        this.suggestionLineLimit = suggestionLineLimit;
        this.anchorToBottom = anchorToBottom;
        this.fillColor = fillColor;
        input.setFormatter(this::formatChat);
    }

    /** Turns the box into an expression box: one getter to mapper chain, read as text. */
    public void setExpressionMode(boolean expressionMode) {
        this.expressionMode = expressionMode;
    }

    /** Whether the suggestion popup is up, so callers can keep tooltips out from under it. */
    public boolean isShowingSuggestions() {
        return this.suggestions != null;
    }

    public void setAllowSuggestions(boolean bl) {
        this.allowSuggestions = bl;
        if (!bl) {
            this.suggestions = null;
        }
    }

    public boolean keyPressed(int i, int j, int k) {
        if (this.suggestions != null && this.suggestions.keyPressed(i, j, k)) {
            return true;
        } else if (this.screen.getFocused() == this.input && i == 258) {
            this.showSuggestions(true);
            return true;
        } else {
            return false;
        }
    }

    public boolean mouseScrolled(double d) {
        return this.suggestions != null && this.suggestions.mouseScrolled(Mth.clamp(d, -1.0, 1.0));
    }

    public boolean mouseClicked(double d, double e, int i) {
        return this.suggestions != null && this.suggestions.mouseClicked((int)d, (int)e, i);
    }

    public void showSuggestions(boolean bl) {
        if (this.pendingSuggestions != null && this.pendingSuggestions.isDone()) {
            Suggestions suggestions = this.pendingSuggestions.join();
            if (!suggestions.isEmpty()) {
                int i = 0;

                for (Suggestion suggestion : suggestions.getList()) {
                    i = Math.max(i, this.font.width(suggestion.getText()));
                }

                // Adjust suggestion position to account for line offset
                int lineStartPos = getLineStartPosition(getCurrentLineNumber());
                int absoluteStartPos = suggestions.getRange().getStart() + lineStartPos;

                int j = Mth.clamp(this.input.getScreenX(absoluteStartPos), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - i);

                int lineHeight = 10; // LINE_HEIGHT from MultiLineEditBox
                int lineY = this.input.getScreenY(this.input.getCursorPosition());
                int k = this.anchorToBottom ? lineY - 3 : lineY + lineHeight + 3;

                this.suggestions = new MultiLineCommandSuggestions.SuggestionsList(j, k, i, this.sortSuggestions(suggestions), bl);
            }
        }
    }

    public void hide() {
        this.suggestions = null;
    }

    private List<Suggestion> sortSuggestions(Suggestions suggestions) {
        String currentLine = getCurrentLine();
        int lineCursorPos = this.input.getCursorPosition() - getLineStartPosition(getCurrentLineNumber());

        String string = currentLine.substring(0, Mth.clamp(lineCursorPos, 0, currentLine.length()));
        int i = getLastWordIndex(string);
        String string2 = string.substring(i).toLowerCase(Locale.ROOT);
        List<Suggestion> list = Lists.<Suggestion>newArrayList();
        List<Suggestion> list2 = Lists.<Suggestion>newArrayList();

        for (Suggestion suggestion : suggestions.getList()) {
            if (!suggestion.getText().startsWith(string2) && !suggestion.getText().startsWith("minecraft:" + string2)) {
                list2.add(suggestion);
            } else {
                list.add(suggestion);
            }
        }

        list.addAll(list2);
        return list;
    }

    public void updateCommandInfo() {
        String currentLine = getCurrentLine();
        int currentLineNumber = getCurrentLineNumber();
        int lineCursorPos = Mth.clamp(this.input.getCursorPosition() - getLineStartPosition(currentLineNumber),
                0, currentLine.length());

        this.currentParse = null;
        this.currentMap = SourceMap.IDENTITY;
        this.currentOffset = 0;

        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }

        this.commandUsage.clear();

        ScriptView<SharedSuggestionProvider> view = ClientScripts.view();
        if (view == null || this.minecraft.getConnection() == null) {
            this.pendingSuggestions = Suggestions.empty();
            return;
        }

        if (this.expressionMode) {
            updateExpressionInfo(view, currentLine, lineCursorPos);
            return;
        }

        if (isAliasDefinitionLine(currentLine) || isAliasDefinitionPrefixLine(currentLine)) {
            updateAliasDefinitionInfo(view, currentLine, lineCursorPos, currentLineNumber);
            return;
        }
        if (isCommentLine(currentLine)) {
            this.pendingSuggestions = Suggestions.empty();
            return;
        }

        int offset = currentLine.startsWith("/") ? 1 : 0;
        if (!this.commandsOnly && offset == 0) {
            String typed = currentLine.substring(0, lineCursorPos);
            Collection<String> collection = this.minecraft.getConnection().getSuggestionsProvider().getCustomTabSugggestions();
            this.pendingSuggestions = SharedSuggestionProvider.suggest(collection, new SuggestionsBuilder(typed, getLastWordIndex(typed)));
            return;
        }

        String command = currentLine.substring(offset);
        int cursor = lineCursorPos - offset;
        this.currentOffset = offset;
        if (isWaitLine(command)) {
            updateWaitInfo(command, offset);
            return;
        }

        SharedSuggestionProvider source = source();
        // Only the aliases defined above this line.
        CommandPreProcessor.Prepared script = prepared(view).script().at(currentLineNumber);
        PreProcessed processed = script.process(command, new PreProcessContext(view.probe(source), null));
        this.currentParse = view.parse(processed.command(), source);
        this.currentMap = processed.sourceMap();

        int shownFrom = this.onlyShowIfCursorPastError ? offset : 1;
        if (lineCursorPos >= shownFrom && (this.suggestions == null || !this.keepSuggestions) && cursor >= 0) {
            CompletableFuture<Suggestions> found = view.suggest(command, cursor, source, script)
                    .thenApply(suggestions -> withWait(command, cursor, suggestions))
                    .thenApply(suggestions -> shifted(suggestions, offset, currentLine));
            this.pendingSuggestions = found;
            found.thenRun(() -> {
                if (this.pendingSuggestions == found && found.isDone()) {
                    this.updateUsageInfo();
                }
            });
        }
    }

    /** Offers {@code wait} alongside the commands, while the first word is being written. */
    private static Suggestions withWait(String command, int cursor, Suggestions suggestions) {
        String typed = command.substring(0, Math.min(cursor, command.length()));
        if (typed.contains(" ") || !WAIT.startsWith(typed)) {
            return suggestions;
        }
        List<Suggestion> list = new ArrayList<>(suggestions.getList());
        list.add(new Suggestion(StringRange.between(0, typed.length()), WAIT));
        return Suggestions.create(command, list);
    }

    private static Suggestions shifted(Suggestions suggestions, int offset, String line) {
        if (offset == 0) {
            return suggestions;
        }
        List<Suggestion> list = new ArrayList<>();
        for (Suggestion suggestion : suggestions.getList()) {
            StringRange range = suggestion.getRange();
            list.add(new Suggestion(StringRange.between(range.getStart() + offset, range.getEnd() + offset),
                    suggestion.getText(), suggestion.getTooltip()));
        }
        return Suggestions.create(line, list);
    }

    private SharedSuggestionProvider source() {
        return ScriptSyntaxHighlighter.source(this.connectedTargets);
    }

    /** The script's pre-processing: its aliases, then its addresses. */
    private PreparedScript prepared(ScriptView<SharedSuggestionProvider> view) {
        String text = this.input.getValue();
        Map<String, BlockPos> addresses = this.addresses.get();
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
    private CommandPreProcessor.Prepared preparedBefore(ScriptView<SharedSuggestionProvider> view, int lineNumber) {
        PreparedScript script = prepared(view);
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

    private static int getLastWordIndex(String string) {
        if (Strings.isNullOrEmpty(string)) {
            return 0;
        } else {
            int i = 0;
            Matcher matcher = WHITESPACE_PATTERN.matcher(string);

            while (matcher.find()) {
                i = matcher.end();
            }

            return i;
        }
    }

    private int getCurrentLineNumber() {
        String value = this.input.getValue();
        int cursorPos = this.input.getCursorPosition();
        int charCount = 0;

        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineLength = lines[i].length();
            if (cursorPos >= charCount && cursorPos <= charCount + lineLength) {
                return i;
            }
            charCount += lineLength + 1; // +1 for newline
        }
        return Math.max(0, lines.length - 1);
    }

    private int getLineStartPosition(int lineNumber) {
        String value = this.input.getValue();
        String[] lines = value.split("\n", -1);
        int position = 0;

        for (int i = 0; i < lineNumber && i < lines.length; i++) {
            position += lines[i].length() + 1; // +1 for newline
        }
        return position;
    }

    private String getCurrentLine() {
        String value = this.input.getValue();
        int lineNumber = getCurrentLineNumber();
        String[] lines = value.split("\n", -1);
        if (lineNumber >= 0 && lineNumber < lines.length) {
            return lines[lineNumber];
        }
        return "";
    }

    // --- lines the editor reads itself -------------------------------------------------------------

    /** A {@code #def} line, which may stand on any line; the lines below it may use its alias. */
    private static boolean isAliasDefinitionLine(String line) {
        return startsWithDefinitionKeyword(line);
    }

    /** A line on its way to being {@code #def}, so that it is offered. */
    private static boolean isAliasDefinitionPrefixLine(String line) {
        return isDefinitionKeywordPrefix(line.stripLeading());
    }

    /** A comment: any other {@code #} line, which may stand on any line. */
    private static boolean isCommentLine(String line) {
        return line.stripLeading().startsWith("#");
    }

    private static boolean startsWithDefinitionKeyword(String line) {
        String stripped = line.stripLeading();
        return stripped.startsWith("#def") && (stripped.length() == 4 || Character.isWhitespace(stripped.charAt(4)));
    }

    private static boolean isDefinitionKeywordPrefix(String strippedLine) {
        return strippedLine.startsWith("#") && "#def".startsWith(strippedLine);
    }

    private static boolean isWaitLine(String command) {
        return command.equals(WAIT) || command.startsWith(WAIT + " ");
    }

    private void updateWaitInfo(String command, int offset) {
        this.pendingSuggestions = Suggestions.empty();
        String ticks = command.substring(WAIT.length()).strip();
        if (ticks.isEmpty() || !validWait(ticks)) {
            Style style = ticks.isEmpty() ? ScriptSyntaxHighlighter.DEFAULT_STYLE : ScriptSyntaxHighlighter.UNPARSED_STYLE;
            this.commandUsage.add(FormattedCharSequence.forward(WAIT_USAGE, style));
            int width = this.font.width(WAIT_USAGE);
            int absoluteStart = getLineStartPosition(getCurrentLineNumber()) + offset + WAIT.length() + 1;
            this.commandUsagePosition = Mth.clamp(this.input.getScreenX(absoluteStart), 0,
                    this.input.getScreenX(0) + this.input.getInnerWidth() - width);
            this.commandUsageWidth = width;
        }
    }

    private static boolean validWait(String ticks) {
        try {
            int value = Integer.parseInt(ticks);
            return value >= 1 && value <= MAX_WAIT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The whole line is the expression, so there is no prefix to step over. */
    private void updateExpressionInfo(ScriptView<SharedSuggestionProvider> view, String currentLine, int lineCursorPos) {
        this.commandUsagePosition = this.input.getScreenX(this.input.getCursorPosition());
        this.commandUsageWidth = this.screen.width;

        this.pendingSuggestions = view.suggestExpression(currentLine, lineCursorPos, source(), null, null);
        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    private void updateAliasDefinitionInfo(ScriptView<SharedSuggestionProvider> view, String currentLine,
                                           int lineCursorPos, int currentLineNumber) {
        this.commandUsagePosition = this.input.getScreenX(this.input.getCursorPosition());
        this.commandUsageWidth = this.screen.width;

        if (currentLine.indexOf('=') != -1) {
            for (PreProcessDiagnostic diagnostic : preparedBefore(view, currentLineNumber + 1).diagnostics()) {
                if (diagnostic.line() != null && diagnostic.line() == currentLineNumber) {
                    this.commandUsage.add(FormattedCharSequence.forward(diagnostic.message(), ScriptSyntaxHighlighter.UNPARSED_STYLE));
                }
            }
        } else if (hasTextInsteadOfEquals(currentLine)) {
            this.commandUsage.add(FormattedCharSequence.forward(EXPECTED_EQUALS_MESSAGE, ScriptSyntaxHighlighter.UNPARSED_STYLE));
        }

        this.pendingSuggestions = aliasDefinitionSuggestions(view, currentLine, lineCursorPos, currentLineNumber);
        if (this.pendingSuggestions.join().isEmpty() && isCursorInAliasNamePosition(currentLine, lineCursorPos)) {
            addAliasNameUsageHint(currentLine, currentLineNumber);
        }
        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    private boolean isCursorInAliasNamePosition(String currentLine, int lineCursorPos) {
        int nameStart = aliasNameStart(currentLine);
        if (nameStart == -1 || lineCursorPos < nameStart || isCursorInEqualsPosition(currentLine, lineCursorPos)) {
            return false;
        }
        int equals = currentLine.indexOf('=');
        return equals == -1 || lineCursorPos <= equals;
    }

    private static boolean isCursorInEqualsPosition(String currentLine, int lineCursorPos) {
        int nameEnd = aliasNameEnd(currentLine);
        return nameEnd != -1
                && nameEnd < currentLine.length()
                && Character.isWhitespace(currentLine.charAt(nameEnd))
                && lineCursorPos > nameEnd;
    }

    private void addAliasNameUsageHint(String currentLine, int currentLineNumber) {
        boolean positionUsage = this.commandUsage.isEmpty();
        this.commandUsage.add(FormattedCharSequence.forward(ALIAS_NAME_USAGE, ScriptSyntaxHighlighter.DEFAULT_STYLE));
        if (!positionUsage) {
            return;
        }

        int width = this.font.width(ALIAS_NAME_USAGE);
        int absoluteStart = getLineStartPosition(currentLineNumber) + aliasNameStart(currentLine);
        this.commandUsagePosition = Mth.clamp(this.input.getScreenX(absoluteStart), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - width);
        this.commandUsageWidth = width;
    }

    /** Index where the alias name begins, or -1 if the {@code #def} keyword is not yet followed by whitespace. */
    private static int aliasNameStart(String line) {
        int cursor = 0;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        if (!line.startsWith("#def", cursor)) {
            return -1;
        }

        cursor += "#def".length();
        if (cursor >= line.length() || !Character.isWhitespace(line.charAt(cursor))) {
            return -1;
        }
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    /** Index just past the alias name, or -1 if no valid name has been typed yet. */
    private static int aliasNameEnd(String line) {
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
    private static int equalsSlotStart(String line) {
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

    /** True when something other than {@code =} has been typed where the {@code =} belongs. */
    private static boolean hasTextInsteadOfEquals(String line) {
        if (line.indexOf('=') != -1) {
            return false;
        }
        int equalsSlot = equalsSlotStart(line);
        return equalsSlot != -1 && equalsSlot < line.length();
    }

    /** Where the expression of an {@code #def} line starts: past the {@code =} and any spaces up to {@code limit}. */
    private static int expressionStart(String line, int limit) {
        int start = line.indexOf('=') + 1;
        while (start < limit && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        return start;
    }

    private CompletableFuture<Suggestions> aliasDefinitionSuggestions(ScriptView<SharedSuggestionProvider> view,
                                                                      String currentLine, int lineCursorPos,
                                                                      int currentLineNumber) {
        int equals = currentLine.indexOf('=');
        if (equals == -1 || lineCursorPos <= equals) {
            String stripped = currentLine.stripLeading();
            if (isDefinitionKeywordPrefix(stripped)) {
                int keywordStart = currentLine.length() - stripped.length();
                SuggestionsBuilder builder = new SuggestionsBuilder(currentLine, keywordStart);
                builder.suggest("#def ");
                return builder.buildFuture();
            }

            // Only whitespace after the name: the '=' still has to be typed.
            int equalsSlot = equalsSlotStart(currentLine);
            if (equals == -1 && equalsSlot >= currentLine.length() && isCursorInEqualsPosition(currentLine, lineCursorPos)) {
                SuggestionsBuilder builder = new SuggestionsBuilder(currentLine, equalsSlot);
                builder.suggest("= ");
                return builder.buildFuture();
            }
            return Suggestions.empty();
        }

        // Never skip whitespace past the cursor: with the cursor inside the run of spaces after the '='
        // the expression is still empty and starts where the cursor is.
        int start = expressionStart(currentLine, lineCursorPos);
        String expression = currentLine.substring(start);
        return view.suggestExpression(expression, lineCursorPos - start, source(), null,
                        preparedBefore(view, currentLineNumber))
                .thenApply(suggestions -> shifted(suggestions, start, currentLine));
    }

    // --- usage ---------------------------------------------------------------------------------------

    private static FormattedCharSequence getExceptionMessage(CommandSyntaxException commandSyntaxException) {
        Component component = ComponentUtils.fromMessage(commandSyntaxException.getRawMessage());
        String string = commandSyntaxException.getContext();
        return string == null
                ? component.getVisualOrderText()
                : Component.translatable("command.context.parse_error", component, commandSyntaxException.getCursor(), string).getVisualOrderText();
    }

    private void updateUsageInfo() {
        ParseResults<SharedSuggestionProvider> parse = this.currentParse;
        if (parse == null || this.pendingSuggestions == null) {
            return;
        }
        boolean bl = false;
        String currentLine = getCurrentLine();
        int lineCursorPos = this.input.getCursorPosition() - getLineStartPosition(getCurrentLineNumber());
        boolean hasArgumentPlaceholder = hasArgumentPlaceholder(parse);

        if (lineCursorPos == currentLine.length()) {
            if (this.pendingSuggestions.join().isEmpty() && !parse.getExceptions().isEmpty() && !hasArgumentPlaceholder) {
                int i = 0;

                for (Entry<CommandNode<SharedSuggestionProvider>, CommandSyntaxException> entry : parse.getExceptions().entrySet()) {
                    CommandSyntaxException commandSyntaxException = entry.getValue();
                    if (commandSyntaxException.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()) {
                        i++;
                    } else {
                        this.commandUsage.add(getExceptionMessage(commandSyntaxException));
                    }
                }

                if (i > 0) {
                    this.commandUsage.add(getExceptionMessage(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()));
                }
            } else if (parse.getReader().canRead() && !hasArgumentPlaceholder) {
                bl = true;
            }
        }

        this.commandUsagePosition = 0;
        this.commandUsageWidth = this.screen.width;
        if (this.commandUsage.isEmpty() && !this.fillNodeUsage(parse, lineCursorPos) && bl && !hasArgumentPlaceholder) {
            this.commandUsage.add(getExceptionMessage(Commands.getParseException(parse)));
        }

        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    /**
     * What the next argument wants, under where it starts. The parse is of the line after
     * pre-processing, so it is only placed when the cursor is at the end of the line or nothing was
     * rewritten; anywhere else it could be placed against the wrong word.
     */
    private boolean fillNodeUsage(ParseResults<SharedSuggestionProvider> parse, int lineCursorPos) {
        String processed = parse.getReader().getString();
        String command = getCurrentLine().substring(this.currentOffset);
        int cursor = lineCursorPos - this.currentOffset;
        int processedCursor;
        if (this.currentMap == SourceMap.IDENTITY) {
            processedCursor = cursor;
        } else if (cursor == command.length()) {
            processedCursor = processed.length();
        } else {
            return false;
        }
        if (processedCursor < 0 || processedCursor > processed.length()) {
            return false;
        }

        CommandContextBuilder<SharedSuggestionProvider> commandContextBuilder = parse.getContext();
        SuggestionContext<SharedSuggestionProvider> suggestionContext = commandContextBuilder.findSuggestionContext(processedCursor);
        Map<CommandNode<SharedSuggestionProvider>, String> map =
                new CommandDispatcher<SharedSuggestionProvider>().getSmartUsage(suggestionContext.parent, source());
        List<FormattedCharSequence> list = Lists.<FormattedCharSequence>newArrayList();
        int i = 0;
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY);

        for (Entry<CommandNode<SharedSuggestionProvider>, String> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof LiteralCommandNode)) {
                String usage = simplifyUsage(entry.getValue());
                list.add(FormattedCharSequence.forward(usage, style));
                i = Math.max(i, this.font.width(usage));
            }
        }

        if (!list.isEmpty()) {
            this.commandUsage.addAll(list);
            int start = this.currentMap.originalStart(suggestionContext.startPos) + this.currentOffset;
            int absoluteStartPos = start + getLineStartPosition(getCurrentLineNumber());
            this.commandUsagePosition = Mth.clamp(this.input.getScreenX(absoluteStartPos), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - i);
            this.commandUsageWidth = i;
            return true;
        } else {
            return false;
        }
    }

    /** An argument's usage as a player reads it: its hint, without the internal name or where it leads. */
    private static String simplifyUsage(String s) {
        int arrowIndex = s.indexOf(" -> ");
        if (arrowIndex != -1) {
            s = s.substring(0, arrowIndex);
        }
        Matcher named = NAMED_ARGUMENT_USAGE.matcher(s);
        return named.replaceAll("<$1>");
    }

    // --- highlighting ----------------------------------------------------------------------------------

    private FormattedCharSequence formatChat(String string, int i, int lineNumber) {
        // 'string' is the visible portion of a line (after horizontal scrolling)
        // 'i' is the horizontal scroll offset within that line (displayPos)
        String[] lines = this.input.getValue().split("\n", -1);
        String fullLine = lineNumber >= 0 && lineNumber < lines.length ? lines[lineNumber] : string;

        // The visible window must stay inside 'fullLine': the scroll offset can outrun a short
        // line, and 'fullLine' may be stale relative to the string handed to us by the widget.
        int visibleStart = Mth.clamp(i, 0, fullLine.length());
        int visibleLength = Math.min(string.length(), fullLine.length() - visibleStart);

        ScriptView<SharedSuggestionProvider> view = ClientScripts.view();
        if (view == null || this.minecraft.getConnection() == null) {
            return FormattedCharSequence.forward(string, Style.EMPTY);
        }
        try {
            List<ScriptSyntaxHighlighter.Span> spans = lineSpans(view, fullLine, lineNumber);
            return ScriptSyntaxHighlighter.sequence(fullLine, spans, visibleStart, visibleLength);
        } catch (RuntimeException e) {
            return FormattedCharSequence.forward(string, Style.EMPTY);
        }
    }

    /** One line's colouring, kept until the script changes since it costs a parse to work out. */
    private List<ScriptSyntaxHighlighter.Span> lineSpans(ScriptView<SharedSuggestionProvider> view, String line,
                                                         int lineNumber) {
        PreparedScript script = prepared(view);
        String key = lineNumber + "\n" + line;
        List<ScriptSyntaxHighlighter.Span> cached = this.highlightCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<ScriptSyntaxHighlighter.Span> spans;
        if (this.expressionMode) {
            spans = ScriptSyntaxHighlighter.expressionSpans(line, 0, line, null, source());
        } else if (lineNumber >= 0 && isAliasDefinitionLine(line)) {
            spans = aliasDefinitionSpans(view, line, lineNumber);
        } else if (lineNumber >= 0 && isCommentLine(line)) {
            spans = List.of();
        } else if (this.commandsOnly || line.startsWith("/")) {
            int offset = line.startsWith("/") ? 1 : 0;
            spans = isWaitLine(line.substring(offset))
                    ? waitSpans(line, offset)
                    : ScriptSyntaxHighlighter.commandSpans(line, script.script().at(lineNumber), source());
        } else {
            spans = List.of();
        }
        this.highlightCache.put(key, spans);
        return spans;
    }

    private static List<ScriptSyntaxHighlighter.Span> waitSpans(String line, int offset) {
        List<ScriptSyntaxHighlighter.Span> spans = new ArrayList<>();
        int keywordEnd = offset + WAIT.length();
        spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(offset, keywordEnd), ScriptSyntaxHighlighter.EXECUTOR_STYLE));
        int start = keywordEnd;
        while (start < line.length() && line.charAt(start) == ' ') {
            start++;
        }
        if (start < line.length()) {
            String ticks = line.substring(start).strip();
            spans.add(new ScriptSyntaxHighlighter.Span(StringRange.between(start, line.length()), validWait(ticks)
                    ? ScriptSyntaxHighlighter.ARGUMENT_STYLE
                    : ScriptSyntaxHighlighter.UNPARSED_STYLE));
        }
        return spans;
    }

    /** {@code #def name = expression}: the keyword and {@code =} plain, the name as a getter, the expression as one. */
    private List<ScriptSyntaxHighlighter.Span> aliasDefinitionSpans(ScriptView<SharedSuggestionProvider> view,
                                                                    String line, int lineNumber) {
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
                    preparedBefore(view, lineNumber), source()));
        }
        return spans;
    }

    @Nullable
    static String calculateSuggestionSuffix(String string, String string2) {
        return string2.startsWith(string) ? string2.substring(string.length()) : null;
    }

    private static boolean hasArgumentPlaceholder(@Nullable ParseResults<SharedSuggestionProvider> parseResults) {
        if (parseResults == null || !parseResults.getReader().canRead()) {
            return false;
        }

        String input = parseResults.getReader().getString();
        int cursor = parseResults.getReader().getCursor();
        return ScriptSyntaxHighlighter.isArgumentPlaceholderAt(input, cursor);
    }

    public void render(GuiGraphics arg, int i, int j) {
        if (!this.renderSuggestions(arg, i, j)) {
            this.renderUsage(arg);
        }
    }

    public boolean renderSuggestions(GuiGraphics arg, int i, int j) {
        if (this.suggestions != null) {
            this.suggestions.render(arg, i, j);
            return true;
        } else {
            return false;
        }
    }

    public void renderUsage(GuiGraphics arg) {
        int lineHeight = 10; // LINE_HEIGHT from MultiLineEditBox
        int lineY = this.input.getScreenY(this.input.getCursorPosition());
        int baseY = this.anchorToBottom ? lineY - 14 - 13 : lineY + lineHeight + 3;

        int i = 0;
        for (FormattedCharSequence formattedCharSequence : this.commandUsage) {
            int j = baseY + (12 * i);
            arg.fill(this.commandUsagePosition - 1, j, this.commandUsagePosition + this.commandUsageWidth + 1, j + 12, this.fillColor);
            arg.drawString(this.font, formattedCharSequence, this.commandUsagePosition, j + 2, -1);
            i++;
        }
    }

    public Component getNarrationMessage() {
        return (Component)(this.suggestions != null ? CommonComponents.NEW_LINE.copy().append(this.suggestions.getNarrationMessage()) : CommonComponents.EMPTY);
    }

    @OnlyIn(Dist.CLIENT)
    public class SuggestionsList {
        private final Rect2i rect;
        private final String originalContents;
        private final int lineNumber;
        private final List<Suggestion> suggestionList;
        private int offset;
        private int current;
        private Vec2 lastMouse = Vec2.ZERO;
        private boolean tabCycles;
        private int lastNarratedEntry;

        SuggestionsList(int i, int j, int k, List<Suggestion> list, boolean bl) {
            int l = i - 1;
            int m = MultiLineCommandSuggestions.this.anchorToBottom ? j - 3 - Math.min(list.size(), MultiLineCommandSuggestions.this.suggestionLineLimit) * 12 : j;
            this.rect = new Rect2i(l, m, k + 1, Math.min(list.size(), MultiLineCommandSuggestions.this.suggestionLineLimit) * 12);
            // Store the current line and line number instead of full text
            this.originalContents = MultiLineCommandSuggestions.this.getCurrentLine();
            this.lineNumber = MultiLineCommandSuggestions.this.getCurrentLineNumber();
            this.lastNarratedEntry = bl ? -1 : 0;
            this.suggestionList = list;
            this.select(0);
        }

        public void render(GuiGraphics arg, int i, int j) {
            int k = Math.min(this.suggestionList.size(), MultiLineCommandSuggestions.this.suggestionLineLimit);
            int l = -5592406;
            boolean bl = this.offset > 0;
            boolean bl2 = this.suggestionList.size() > this.offset + k;
            boolean bl3 = bl || bl2;
            boolean bl4 = this.lastMouse.x != i || this.lastMouse.y != j;
            if (bl4) {
                this.lastMouse = new Vec2(i, j);
            }

            if (bl3) {
                arg.fill(this.rect.getX(), this.rect.getY() - 1, this.rect.getX() + this.rect.getWidth(), this.rect.getY(), MultiLineCommandSuggestions.this.fillColor);
                arg.fill(
                        this.rect.getX(),
                        this.rect.getY() + this.rect.getHeight(),
                        this.rect.getX() + this.rect.getWidth(),
                        this.rect.getY() + this.rect.getHeight() + 1,
                        MultiLineCommandSuggestions.this.fillColor
                );
                if (bl) {
                    for (int m = 0; m < this.rect.getWidth(); m++) {
                        if (m % 2 == 0) {
                            arg.fill(this.rect.getX() + m, this.rect.getY() - 1, this.rect.getX() + m + 1, this.rect.getY(), -1);
                        }
                    }
                }

                if (bl2) {
                    for (int mx = 0; mx < this.rect.getWidth(); mx++) {
                        if (mx % 2 == 0) {
                            arg.fill(this.rect.getX() + mx, this.rect.getY() + this.rect.getHeight(), this.rect.getX() + mx + 1, this.rect.getY() + this.rect.getHeight() + 1, -1);
                        }
                    }
                }
            }

            boolean bl5 = false;

            for (int n = 0; n < k; n++) {
                Suggestion suggestion = (Suggestion)this.suggestionList.get(n + this.offset);
                arg.fill(
                        this.rect.getX(), this.rect.getY() + 12 * n, this.rect.getX() + this.rect.getWidth(), this.rect.getY() + 12 * n + 12, MultiLineCommandSuggestions.this.fillColor
                );
                if (i > this.rect.getX() && i < this.rect.getX() + this.rect.getWidth() && j > this.rect.getY() + 12 * n && j < this.rect.getY() + 12 * n + 12) {
                    if (bl4) {
                        this.select(n + this.offset);
                    }

                    bl5 = true;
                }

                arg.drawString(
                        MultiLineCommandSuggestions.this.font, suggestion.getText(), this.rect.getX() + 1, this.rect.getY() + 2 + 12 * n, n + this.offset == this.current ? -256 : -5592406
                );
            }

            if (bl5) {
                Message message = ((Suggestion)this.suggestionList.get(this.current)).getTooltip();
                if (message != null) {
                    arg.renderTooltip(MultiLineCommandSuggestions.this.font, ComponentUtils.fromMessage(message), i, j);
                }
            }
        }

        public boolean mouseClicked(int i, int j, int k) {
            if (!this.rect.contains(i, j)) {
                return false;
            } else {
                int l = (j - this.rect.getY()) / 12 + this.offset;
                if (l >= 0 && l < this.suggestionList.size()) {
                    this.select(l);
                    this.useSuggestion();
                }

                return true;
            }
        }

        public boolean mouseScrolled(double d) {
            int i = (int)(
                    MultiLineCommandSuggestions.this.minecraft.mouseHandler.xpos()
                            * MultiLineCommandSuggestions.this.minecraft.getWindow().getGuiScaledWidth()
                            / MultiLineCommandSuggestions.this.minecraft.getWindow().getScreenWidth()
            );
            int j = (int)(
                    MultiLineCommandSuggestions.this.minecraft.mouseHandler.ypos()
                            * MultiLineCommandSuggestions.this.minecraft.getWindow().getGuiScaledHeight()
                            / MultiLineCommandSuggestions.this.minecraft.getWindow().getScreenHeight()
            );
            if (this.rect.contains(i, j)) {
                this.offset = Mth.clamp((int)(this.offset - d), 0, Math.max(this.suggestionList.size() - MultiLineCommandSuggestions.this.suggestionLineLimit, 0));
                return true;
            } else {
                return false;
            }
        }

        public boolean keyPressed(int i, int j, int k) {
            if (i == 265) {
                this.cycle(-1);
                this.tabCycles = false;
                return true;
            } else if (i == 264) {
                this.cycle(1);
                this.tabCycles = false;
                return true;
            } else if (i == 258) {
                if (this.tabCycles) {
                    this.cycle(Screen.hasShiftDown() ? -1 : 1);
                }

                this.useSuggestion();
                return true;
            } else if (i == 256) {
                MultiLineCommandSuggestions.this.hide();
                return true;
            } else {
                return false;
            }
        }

        public void cycle(int i) {
            this.select(this.current + i);
            int j = this.offset;
            int k = this.offset + MultiLineCommandSuggestions.this.suggestionLineLimit - 1;
            if (this.current < j) {
                this.offset = Mth.clamp(this.current, 0, Math.max(this.suggestionList.size() - MultiLineCommandSuggestions.this.suggestionLineLimit, 0));
            } else if (this.current > k) {
                // The selection becomes the last row shown. Vanilla adds lineStartOffset here, which is
                // only right for chat's 1; with 0 the selection sits one row below the list, unseen.
                this.offset = Mth.clamp(
                        this.current + 1 - MultiLineCommandSuggestions.this.suggestionLineLimit,
                        0,
                        Math.max(this.suggestionList.size() - MultiLineCommandSuggestions.this.suggestionLineLimit, 0)
                );
            }
        }

        public void select(int i) {
            this.current = i;
            if (this.current < 0) {
                this.current = this.current + this.suggestionList.size();
            }

            if (this.current >= this.suggestionList.size()) {
                this.current = this.current - this.suggestionList.size();
            }

            Suggestion suggestion = (Suggestion)this.suggestionList.get(this.current);
            // Use current line for suggestion suffix calculation
            String currentLine = MultiLineCommandSuggestions.this.getCurrentLine();
            MultiLineCommandSuggestions.this.input
                    .setSuggestion(MultiLineCommandSuggestions.calculateSuggestionSuffix(currentLine, suggestion.apply(this.originalContents)));
            if (this.lastNarratedEntry != this.current) {
                MultiLineCommandSuggestions.this.minecraft.getNarrator().sayNow(this.getNarrationMessage());
            }
        }

        public void useSuggestion() {
            Suggestion suggestion = (Suggestion)this.suggestionList.get(this.current);
            boolean reopenSuggestions = "value_of(".equals(suggestion.getText());
            MultiLineCommandSuggestions.this.keepSuggestions = true;

            // Apply suggestion to current line only
            String newLineContent = suggestion.apply(this.originalContents);

            // Replace the current line in the full text
            String fullValue = MultiLineCommandSuggestions.this.input.getValue();
            String[] lines = fullValue.split("\n", -1);
            if (this.lineNumber >= 0 && this.lineNumber < lines.length) {
                lines[this.lineNumber] = newLineContent;
            }
            String newValue = String.join("\n", lines);

            MultiLineCommandSuggestions.this.input.setValue(newValue);

            // Calculate absolute cursor position (line start + position within line)
            int lineStartPos = MultiLineCommandSuggestions.this.getLineStartPosition(this.lineNumber);
            int lineRelativePos = suggestion.getRange().getStart() + suggestion.getText().length();
            int absolutePos = lineStartPos + lineRelativePos;

            MultiLineCommandSuggestions.this.input.setCursorPosition(absolutePos);
            MultiLineCommandSuggestions.this.input.setHighlightPos(absolutePos);
            this.select(this.current);
            MultiLineCommandSuggestions.this.keepSuggestions = false;
            this.tabCycles = true;
            if (reopenSuggestions) {
                MultiLineCommandSuggestions.this.hide();
                MultiLineCommandSuggestions.this.updateCommandInfo();
            }
        }

        Component getNarrationMessage() {
            this.lastNarratedEntry = this.current;
            Suggestion suggestion = (Suggestion)this.suggestionList.get(this.current);
            Message message = suggestion.getTooltip();
            return message != null
                    ? Component.translatable("narration.suggestion.tooltip", this.current + 1, this.suggestionList.size(), suggestion.getText(), ComponentUtils.fromMessage(message))
                    : Component.translatable("narration.suggestion", this.current + 1, this.suggestionList.size(), suggestion.getText());
        }
    }

}
