package g_mungus.zps.client.screens.components;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api_impl.ValueOfDispatchers;
import g_mungus.zps.commands.api_impl.TypeKeys;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.aliases.ScriptAliases;
import g_mungus.zps.commands.api_impl.arguments.OverloadedExecutorArgumentType;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineCommandSuggestions {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");
    private static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String ARGUMENT_PLACEHOLDER = "%s";
    private static final String ALIAS_NAME_USAGE = "<alias>";
    private static final String EXPECTED_EQUALS_MESSAGE = "Expected '=' after alias name";
    private static final ResourceLocation BOOLEAN_TYPE = ResourceLocation.parse("zps:boolean");
    private static final Style UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    public static final int EXECUTOR_COLOR = 0xF5A97F;
    public static final int GETTER_COLOR = 0xC792EA;
    private static final Style EXECUTOR_STYLE = Style.EMPTY.withColor(EXECUTOR_COLOR);
    private static final Style GETTER_STYLE = Style.EMPTY.withColor(GETTER_COLOR);
    private static final Style MAPPER_STYLE = Style.EMPTY.withColor(0x4C99C9);
    private static final Style ARGUMENT_STYLE = Style.EMPTY.withColor(0x79F1A3);
    private static final Style DEFAULT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    final Minecraft minecraft;
    private final CommandDispatcherProvider dispatcherProvider;
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
    @Nullable
    private final Set<ResourceLocation> connectedBlocks;
    private int commandUsagePosition;
    private int commandUsageWidth;
    @Nullable
    private ParseResults<SharedSuggestionProvider> currentParse;
    @Nullable
    private CommandDispatcher<SharedSuggestionProvider> currentDispatcher;
    @Nullable
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Nullable
    private MultiLineCommandSuggestions.SuggestionsList suggestions;
    private boolean allowSuggestions;
    boolean keepSuggestions;

    public MultiLineCommandSuggestions(Minecraft arg, CommandDispatcherProvider dispatcherProvider, Screen arg2, MultiLineEditBox arg3, Font arg4, boolean bl, boolean bl2, int i, int j, boolean bl3, int k, Set<ResourceLocation> connectedBlocks) {
        this.connectedBlocks = connectedBlocks;
        this.minecraft = arg;
        this.dispatcherProvider = dispatcherProvider;
        this.screen = arg2;
        this.input = arg3;
        this.font = arg4;
        this.commandsOnly = bl;
        this.onlyShowIfCursorPastError = bl2;
        this.lineStartOffset = i;
        this.suggestionLineLimit = j;
        this.anchorToBottom = bl3;
        this.fillColor = k;
        arg3.setFormatter(this::formatChat);
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
            Suggestions suggestions = (Suggestions)this.pendingSuggestions.join();
            if (!suggestions.isEmpty()) {
                int i = 0;

                for (Suggestion suggestion : filterByConnectedBlocks(suggestions.getList())) {
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
        // Use current line instead of full value
        String currentLine = getCurrentLine();
        int lineStartPos = getLineStartPosition(getCurrentLineNumber());
        int absoluteCursorPos = this.input.getCursorPosition();
        int lineCursorPos = absoluteCursorPos - lineStartPos;

        String string = currentLine.substring(0, Math.min(lineCursorPos, currentLine.length()));
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
        return filterByConnectedBlocks(list);
    }

    private List<Suggestion> filterByConnectedBlocks(List<Suggestion> list) {
        if (connectedBlocks == null) return list;
        List<Suggestion> output = new ArrayList<>();

        for (var suggestion : list) {
            String command = suggestion.getText();

            boolean knownCommand = false;
            boolean appliesToConnectedBlocks = false;

            for (ScriptExecutor<?, ?> executor : ZPSCommands.getExecutors(command)) {
                knownCommand = true;
                Set<ResourceLocation> associatedBlocks = executor.associatedBlocks();
                if (associatedBlocks == null || associatedBlocks.stream().anyMatch(connectedBlocks::contains)) {
                    appliesToConnectedBlocks = true;
                    break;
                }
            }

            if (!appliesToConnectedBlocks) {
                ScriptGetter<?> getter = ZPSCommands.getGetter(command);
                if (getter != null) {
                    knownCommand = true;
                    Set<ResourceLocation> associatedBlocks = getter.associatedBlocks();
                    appliesToConnectedBlocks = associatedBlocks == null || associatedBlocks.stream().anyMatch(connectedBlocks::contains);
                }
            }

            if (!knownCommand || appliesToConnectedBlocks) {
                output.add(suggestion);
            }
        }

        return output;
    }

    public void updateCommandInfo() {
        // Get current line instead of full text
        String currentLine = getCurrentLine();
        int lineStartPos = getLineStartPosition(getCurrentLineNumber());
        int absoluteCursorPos = this.input.getCursorPosition();
        int lineCursorPos = absoluteCursorPos - lineStartPos;

        this.currentParse = null;
        this.currentDispatcher = null;

        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }

        this.commandUsage.clear();
        int currentLineNumber = getCurrentLineNumber();
        ValueOfOrLiteralArgumentType.setActiveExpressionAliases(aliasesBeforeLine(currentLineNumber));
        OverloadedExecutorArgumentType.setActiveConnectedBlocks(connectedBlocks);

        StringReader stringReader = new StringReader(currentLine);
        boolean bl = stringReader.canRead() && stringReader.peek() == '/';
        if (bl) {
            stringReader.skip();
        }

        if (isLeadingAliasDefinitionLine(currentLineNumber, currentLine)
                || isAliasDefinitionPrefixLine(currentLineNumber, currentLine)) {
            updateAliasDefinitionInfo(currentLine, lineCursorPos, currentLineNumber);
            return;
        }

        boolean bl2 = this.commandsOnly || bl;
        if (bl2) {
            CommandDispatcher<SharedSuggestionProvider> commandDispatcher = dispatcherWithAliases(currentLineNumber);
            this.currentDispatcher = commandDispatcher;
            if (this.currentParse == null) {
                this.currentParse = commandDispatcher.parse(stringReader, this.minecraft.player.connection.getSuggestionsProvider());
            }

            int j = this.onlyShowIfCursorPastError ? stringReader.getCursor() : 1;
            if (lineCursorPos >= j && (this.suggestions == null || !this.keepSuggestions)) {
                // Get suggestions for the current line with line-relative cursor position
                this.pendingSuggestions = commandDispatcher.getCompletionSuggestions(this.currentParse, lineCursorPos);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) {
                        this.updateUsageInfo();
                    }
                });
            }
        } else {
            String string2 = currentLine.substring(0, Math.min(lineCursorPos, currentLine.length()));
            int j = getLastWordIndex(string2);
            Collection<String> collection = this.minecraft.player.connection.getSuggestionsProvider().getCustomTabSugggestions();
            this.pendingSuggestions = SharedSuggestionProvider.suggest(collection, new SuggestionsBuilder(string2, j));
        }
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
        int lineNumber = 0;
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

    private boolean isLeadingAliasDefinitionLine(int lineNumber, String line) {
        return ScriptAliases.startsWithDefinitionKeyword(line) && !hasEarlierExecutableLine(lineNumber);
    }

    private boolean isAliasDefinitionPrefixLine(int lineNumber, String line) {
        String stripped = line.stripLeading();
        return isDefinitionKeywordPrefix(stripped) && !hasEarlierExecutableLine(lineNumber);
    }

    private static boolean isDefinitionKeywordPrefix(String strippedLine) {
        return strippedLine.startsWith("#") && "#def".startsWith(strippedLine);
    }

    private boolean hasEarlierExecutableLine(int lineNumber) {
        String[] lines = this.input.getValue().split("\n", -1);
        for (int i = 0; i < lineNumber && i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (!trimmed.isBlank() && !trimmed.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    private void updateAliasDefinitionInfo(String currentLine, int lineCursorPos, int currentLineNumber) {
        this.currentParse = null;
        this.commandUsage.clear();
        this.commandUsagePosition = this.input.getScreenX(this.input.getCursorPosition());
        this.commandUsageWidth = this.screen.width;

        if (currentLine.indexOf('=') != -1) {
            ScriptAliases.ParsedScript parsedThroughLine = ScriptAliases.parse(scriptThroughLine(currentLineNumber));
            for (ScriptAliases.Diagnostic diagnostic : parsedThroughLine.diagnostics()) {
                if (diagnostic.lineIndex() == currentLineNumber) {
                    this.commandUsage.add(FormattedCharSequence.forward(diagnostic.message(), UNPARSED_STYLE));
                }
            }
        } else if (hasTextInsteadOfEquals(currentLine)) {
            this.commandUsage.add(FormattedCharSequence.forward(EXPECTED_EQUALS_MESSAGE, UNPARSED_STYLE));
        }

        this.pendingSuggestions = aliasDefinitionSuggestions(currentLine, lineCursorPos, currentLineNumber);
        updateAliasDefinitionUsageInfo(currentLine, lineCursorPos, currentLineNumber);
        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    private void updateAliasDefinitionUsageInfo(String currentLine, int lineCursorPos, int currentLineNumber) {
        if (this.pendingSuggestions == null || !this.pendingSuggestions.join().isEmpty()) {
            return;
        }

        if (isCursorInAliasNamePosition(currentLine, lineCursorPos)) {
            addAliasNameUsageHint(currentLine, currentLineNumber);
            return;
        }

        AliasExpressionParse aliasParse = bestAliasExpressionParse(currentLine, lineCursorPos, currentLineNumber);
        if (aliasParse == null) {
            return;
        }

        SuggestionContext<SharedSuggestionProvider> suggestionContext =
                aliasParse.parse().getContext().findSuggestionContext(aliasParse.expressionCursor());
        assert this.minecraft.player != null;
        Map<CommandNode<SharedSuggestionProvider>, String> smartUsage =
                aliasParse.dispatcher().getSmartUsage(suggestionContext.parent, this.minecraft.player.connection.getSuggestionsProvider());

        List<FormattedCharSequence> usageLines = Lists.newArrayList();
        int width = 0;
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY);
        for (Entry<CommandNode<SharedSuggestionProvider>, String> entry : smartUsage.entrySet()) {
            if (entry.getKey() instanceof LiteralCommandNode) {
                continue;
            }
            String usage = simplifyUsage(entry.getValue());
            usageLines.add(FormattedCharSequence.forward(usage, style));
            width = Math.max(width, this.font.width(usage));
        }

        if (usageLines.isEmpty()) {
            return;
        }

        this.commandUsage.addAll(usageLines);
        int absoluteStart = getLineStartPosition(currentLineNumber) + aliasParse.expressionStart() + suggestionContext.startPos;
        this.commandUsagePosition = Mth.clamp(this.input.getScreenX(absoluteStart), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - width);
        this.commandUsageWidth = width;
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
        this.commandUsage.add(FormattedCharSequence.forward(ALIAS_NAME_USAGE, DEFAULT_STYLE));
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

    private CompletableFuture<Suggestions> aliasDefinitionSuggestions(String currentLine, int lineCursorPos, int currentLineNumber) {
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

        int expressionStart = equals + 1;
        while (expressionStart < currentLine.length() && Character.isWhitespace(currentLine.charAt(expressionStart))) {
            expressionStart++;
        }

        String typedExpression = currentLine.substring(expressionStart, Math.min(lineCursorPos, currentLine.length()));
        List<Suggestion> suggestions = new ArrayList<>();

        assert this.minecraft.player != null;
        Map<String, ScriptAliases.AliasDefinition> visibleAliases = aliasesBeforeLine(currentLineNumber);
        for (ResourceLocation typeKey : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            var dispatcher = ValueOfOrLiteralArgumentType.valueOfDispatcherWithExpressionAliases(
                    typeKey,
                    visibleAliases,
                    this.minecraft.player.connection.getSuggestionsProvider()
            );
            if (dispatcher == null) {
                continue;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                var rawDispatcher = (CommandDispatcher) dispatcher;
                ParseResults<SharedSuggestionProvider> parseResults = rawDispatcher.parse(
                        typedExpression,
                        this.minecraft.player.connection.getSuggestionsProvider()
                );
                Suggestions dispatcherSuggestions = (Suggestions) rawDispatcher
                        .getCompletionSuggestions(parseResults, typedExpression.length())
                        .join();
                for (Suggestion suggestion : dispatcherSuggestions.getList()) {
                    if (ValueOfDispatchers.TERMINAL_LITERAL.equals(suggestion.getText())) {
                        continue;
                    }
                    suggestions.add(new Suggestion(
                            StringRange.between(
                                    suggestion.getRange().getStart() + expressionStart,
                                    suggestion.getRange().getEnd() + expressionStart
                            ),
                            suggestion.getText(),
                            suggestion.getTooltip()
                    ));
                }
            } catch (Exception ignored) {
            }
        }

        return CompletableFuture.completedFuture(Suggestions.create(currentLine, suggestions));
    }

    @Nullable
    private AliasExpressionParse bestAliasExpressionParse(String currentLine, int lineCursorPos, int currentLineNumber) {
        int equals = currentLine.indexOf('=');
        if (equals == -1 || lineCursorPos <= equals) {
            return null;
        }

        int expressionStart = equals + 1;
        while (expressionStart < currentLine.length() && Character.isWhitespace(currentLine.charAt(expressionStart))) {
            expressionStart++;
        }

        int expressionCursor = Math.max(0, Math.min(lineCursorPos, currentLine.length()) - expressionStart);
        String typedExpression = currentLine.substring(expressionStart, Math.min(lineCursorPos, currentLine.length()));

        assert this.minecraft.player != null;
        SharedSuggestionProvider source = this.minecraft.player.connection.getSuggestionsProvider();
        Map<String, ScriptAliases.AliasDefinition> visibleAliases = aliasesBeforeLine(currentLineNumber);
        AliasExpressionParse bestParse = null;
        int bestScore = -1;
        for (ResourceLocation typeKey : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            var dispatcher = ValueOfOrLiteralArgumentType.valueOfDispatcherWithExpressionAliases(
                    typeKey,
                    visibleAliases,
                    source
            );
            if (dispatcher == null) {
                continue;
            }
            try {
                ParseResults<SharedSuggestionProvider> parse = dispatcher.parse(
                        typedExpression,
                        source
                );
                int score = parse.getReader().getCursor() * 10 - parse.getExceptions().size();
                if (score > bestScore) {
                    bestScore = score;
                    bestParse = new AliasExpressionParse(dispatcher, parse, expressionStart, expressionCursor);
                }
            } catch (Exception ignored) {
            }
        }
        return bestParse;
    }

    private Map<String, ScriptAliases.AliasDefinition> aliasesBeforeLine(int lineNumber) {
        return ScriptAliases.parse(scriptBeforeLine(lineNumber)).aliases();
    }

    private CommandDispatcher<SharedSuggestionProvider> dispatcherWithAliases(int lineNumber) {
        CommandDispatcher<SharedSuggestionProvider> dispatcher = copyDispatcher(dispatcherProvider.get());
        Map<String, ScriptAliases.AliasDefinition> aliases = aliasesBeforeLine(lineNumber);
        if (aliases.isEmpty()) {
            return dispatcher;
        }

        CommandNode<SharedSuggestionProvider> root = dispatcher.getRoot();
        CommandNode<SharedSuggestionProvider> ifNode = root.getChild("if");
        CommandNode<SharedSuggestionProvider> unlessNode = root.getChild("unless");
        CommandNode<SharedSuggestionProvider> booleanExpressionRoot = firstNonNullRedirect(ifNode, unlessNode);
        if (booleanExpressionRoot == null) {
            return dispatcher;
        }
        for (ScriptAliases.AliasDefinition alias : aliases.values()) {
            ResourceLocation outputType = inferAliasOutputType(alias);
            if (outputType == null) {
                continue;
            }
            CommandNode<SharedSuggestionProvider> aliasDestination =
                    findNode(booleanExpressionRoot, "have-" + outputType + "-need-" + BOOLEAN_TYPE);
            if (aliasDestination != null) {
                booleanExpressionRoot.addChild(aliasCommandNode(alias.name(), aliasDestination));
            }
        }
        return dispatcher;
    }

    @Nullable
    private ResourceLocation inferAliasOutputType(ScriptAliases.AliasDefinition alias) {
        String expression = ScriptAliases.resolveExpression(alias.expression(), alias.visibleAliases());
        assert this.minecraft.player != null;
        for (ResourceLocation typeKey : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            var dispatcher = ValueOfDispatchers.get(typeKey);
            if (dispatcher == null) {
                continue;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                var rawDispatcher = (CommandDispatcher) dispatcher;
                var parse = rawDispatcher.parse(
                        expression + " " + ValueOfDispatchers.TERMINAL_LITERAL,
                        this.minecraft.player.connection.getSuggestionsProvider()
                );
                if (!parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
                    return typeKey;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static CommandNode<SharedSuggestionProvider> firstNonNullRedirect(@Nullable CommandNode<SharedSuggestionProvider> first, @Nullable CommandNode<SharedSuggestionProvider> second) {
        if (first != null && first.getRedirect() != null) {
            return first.getRedirect();
        }
        return second != null ? second.getRedirect() : null;
    }

    @Nullable
    private static CommandNode<SharedSuggestionProvider> findNode(CommandNode<SharedSuggestionProvider> root, String name) {
        Set<CommandNode<SharedSuggestionProvider>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findNode(root, name, visited);
    }

    @Nullable
    private static CommandNode<SharedSuggestionProvider> findNode(
            CommandNode<SharedSuggestionProvider> node,
            String name,
            Set<CommandNode<SharedSuggestionProvider>> visited
    ) {
        if (!visited.add(node)) {
            return null;
        }
        if (node.getName().equals(name)) {
            return node;
        }
        for (CommandNode<SharedSuggestionProvider> child : node.getChildren()) {
            CommandNode<SharedSuggestionProvider> found = findNode(child, name, visited);
            if (found != null) {
                return found;
            }
        }
        if (node.getRedirect() != null) {
            return findNode(node.getRedirect(), name, visited);
        }
        return null;
    }

    private static CommandNode<SharedSuggestionProvider> aliasCommandNode(String aliasName, CommandNode<SharedSuggestionProvider> root) {
        return LiteralArgumentBuilder.<SharedSuggestionProvider>literal(aliasName)
                .redirect(root)
                .build();
    }

    private static CommandDispatcher<SharedSuggestionProvider> copyDispatcher(CommandDispatcher<SharedSuggestionProvider> original) {
        CommandDispatcher<SharedSuggestionProvider> copy = new CommandDispatcher<>();
        Map<CommandNode<SharedSuggestionProvider>, CommandNode<SharedSuggestionProvider>> copiedNodes = new IdentityHashMap<>();
        copiedNodes.put(original.getRoot(), copy.getRoot());
        for (CommandNode<SharedSuggestionProvider> child : original.getRoot().getChildren()) {
            copy.getRoot().addChild(copyNode(child, copiedNodes));
        }
        return copy;
    }

    private static CommandNode<SharedSuggestionProvider> copyNode(
            CommandNode<SharedSuggestionProvider> original,
            Map<CommandNode<SharedSuggestionProvider>, CommandNode<SharedSuggestionProvider>> copiedNodes
    ) {
        CommandNode<SharedSuggestionProvider> existing = copiedNodes.get(original);
        if (existing != null) {
            return existing;
        }

        ArgumentBuilder<SharedSuggestionProvider, ?> builder = original.createBuilder();
        if (original.getRedirect() != null) {
            builder.forward(
                    copyNode(original.getRedirect(), copiedNodes),
                    original.getRedirectModifier(),
                    original.isFork()
            );
        }

        CommandNode<SharedSuggestionProvider> copy = builder.build();
        copiedNodes.put(original, copy);
        for (CommandNode<SharedSuggestionProvider> child : original.getChildren()) {
            copy.addChild(copyNode(child, copiedNodes));
        }
        return copy;
    }

    private String scriptBeforeLine(int lineNumber) {
        String[] lines = this.input.getValue().split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lineNumber && i < lines.length; i++) {
            out.append(lines[i]).append('\n');
        }
        return out.toString();
    }

    private String scriptThroughLine(int lineNumber) {
        String[] lines = this.input.getValue().split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i <= lineNumber && i < lines.length; i++) {
            out.append(lines[i]).append('\n');
        }
        return out.toString();
    }

    private static FormattedCharSequence getExceptionMessage(CommandSyntaxException commandSyntaxException) {
        Component component = ComponentUtils.fromMessage(commandSyntaxException.getRawMessage());
        String string = commandSyntaxException.getContext();
        return string == null
                ? component.getVisualOrderText()
                : Component.translatable("command.context.parse_error", component, commandSyntaxException.getCursor(), string).getVisualOrderText();
    }

    private void updateUsageInfo() {
        boolean bl = false;
        // Check if cursor is at end of current line instead of full text
        String currentLine = getCurrentLine();
        int lineStartPos = getLineStartPosition(getCurrentLineNumber());
        int lineCursorPos = this.input.getCursorPosition() - lineStartPos;
        boolean hasArgumentPlaceholder = hasArgumentPlaceholder(this.currentParse);

        if (lineCursorPos == currentLine.length()) {
            if (((Suggestions)this.pendingSuggestions.join()).isEmpty() && !this.currentParse.getExceptions().isEmpty() && !hasArgumentPlaceholder) {
                int i = 0;

                for (Entry<CommandNode<SharedSuggestionProvider>, CommandSyntaxException> entry : this.currentParse.getExceptions().entrySet()) {
                    CommandSyntaxException commandSyntaxException = (CommandSyntaxException)entry.getValue();
                    if (commandSyntaxException.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()) {
                        i++;
                    } else {
                        this.commandUsage.add(getExceptionMessage(commandSyntaxException));
                    }
                }

                if (i > 0) {
                    this.commandUsage.add(getExceptionMessage(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()));
                }
            } else if (this.currentParse.getReader().canRead() && !hasArgumentPlaceholder) {
                bl = true;
            }
        }

        this.commandUsagePosition = 0;
        this.commandUsageWidth = this.screen.width;
        if (this.commandUsage.isEmpty() && !this.fillNodeUsage(ChatFormatting.GRAY) && bl && !hasArgumentPlaceholder) {
            this.commandUsage.add(getExceptionMessage(Commands.getParseException(this.currentParse)));
        }

        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    private boolean fillNodeUsage(ChatFormatting arg) {
        CommandContextBuilder<SharedSuggestionProvider> commandContextBuilder = this.currentParse.getContext();
        // Use line-relative cursor position
        int lineStartPos = getLineStartPosition(getCurrentLineNumber());
        int lineCursorPos = this.input.getCursorPosition() - lineStartPos;

        SuggestionContext<SharedSuggestionProvider> suggestionContext = commandContextBuilder.findSuggestionContext(lineCursorPos);
        assert this.minecraft.player != null;

        Map<CommandNode<SharedSuggestionProvider>, String> map =
                (this.currentDispatcher != null ? this.currentDispatcher : dispatcherProvider.get())
                .getSmartUsage(suggestionContext.parent, this.minecraft.player.connection.getSuggestionsProvider());
        List<FormattedCharSequence> list = Lists.<FormattedCharSequence>newArrayList();
        int i = 0;
        Style style = Style.EMPTY.withColor(arg);

        for (Entry<CommandNode<SharedSuggestionProvider>, String> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof LiteralCommandNode)) {
                String usage = entry.getValue();

                usage = simplifyUsage(usage);

                list.add(FormattedCharSequence.forward(usage, style));
                i = Math.max(i, this.font.width(usage));
            }
        }


        if (!list.isEmpty()) {
            this.commandUsage.addAll(list);
            // Adjust screen position for line offset
            int absoluteStartPos = suggestionContext.startPos + lineStartPos;
            this.commandUsagePosition = Mth.clamp(this.input.getScreenX(absoluteStartPos), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - i);
            this.commandUsageWidth = i;
            return true;
        } else {
            return false;
        }
    }

    private static String simplifyUsage(String s) {
        if (!s.startsWith("<zps:")) {
            return s;
        }

        // remove any redirect hint
        int arrowIndex = s.indexOf(" -> ");
        if (arrowIndex != -1) {
            s = s.substring(0, arrowIndex);
        }

        int lastColon = s.lastIndexOf(':');
        int closingBracket = s.lastIndexOf('>');

        if (lastColon != -1 && closingBracket != -1 && lastColon < closingBracket) {
            String type = s.substring(lastColon + 1, closingBracket);
            String result = "<" + type + ">";
            if (result.equals("<compute>")) {
                return "compute";
            } else {
                return result;
            }
        }

        return s;
    }



    private FormattedCharSequence formatChat(String string, int i, int lineNumber) {
        // 'string' is the visible portion of a line (after horizontal scrolling)
        // 'i' is the horizontal scroll offset within that line (displayPos)
        String[] lines = this.input.getValue().split("\n", -1);
        String fullLine = lineNumber >= 0 && lineNumber < lines.length ? lines[lineNumber] : string;

        // Parse the full line
        if (lineNumber >= 0 && isLeadingAliasDefinitionLine(lineNumber, fullLine)) {
            return formatAliasDefinitionLine(fullLine, i, string.length(), lineNumber);
        }

        // Parse the full line
        if (this.commandsOnly || (!fullLine.isEmpty() && fullLine.charAt(0) == '/')) {
            StringReader stringReader = new StringReader(fullLine);
            boolean hasSlash = stringReader.canRead() && stringReader.peek() == '/';
            if (hasSlash) {
                stringReader.skip();
            }

            try {
                assert this.minecraft.player != null;
                Map<String, ScriptAliases.AliasDefinition> expressionAliases = lineNumber >= 0
                        ? aliasesBeforeLine(lineNumber)
                        : Map.of();
                CommandDispatcher<SharedSuggestionProvider> commandDispatcher = lineNumber >= 0
                        ? dispatcherWithAliases(lineNumber)
                        : dispatcherProvider.get();
                ParseResults<SharedSuggestionProvider> lineParseResults = commandDispatcher.parse(stringReader, this.minecraft.player.connection.getSuggestionsProvider());

                return formatText(lineParseResults, fullLine, i, string.length(), expressionAliases);
            } catch (Exception e) {
                // If parsing fails, return unformatted
                return FormattedCharSequence.forward(string, Style.EMPTY);
            }
        }
        return FormattedCharSequence.forward(string, Style.EMPTY);
    }

    private FormattedCharSequence formatAliasDefinitionLine(String fullLine, int visibleStart, int visibleLength, int lineNumber) {
        List<FormattedCharSequence> list = Lists.newArrayList();
        List<HighlightSpan> spans = new ArrayList<>();
        collectAliasDefinitionSpans(fullLine, spans, lineNumber);
        spans.sort(Comparator.comparingInt(span -> span.range().getStart()));

        int cursor = 0;
        for (HighlightSpan span : spans) {
            int start = Math.max(span.range().getStart() - visibleStart, 0);
            if (start >= visibleLength) {
                break;
            }
            int end = Math.min(span.range().getEnd() - visibleStart, visibleLength);
            if (end <= start) {
                continue;
            }
            if (start > cursor) {
                list.add(FormattedCharSequence.forward(fullLine.substring(visibleStart + cursor, visibleStart + start), DEFAULT_STYLE));
            }
            list.add(FormattedCharSequence.forward(fullLine.substring(visibleStart + start, visibleStart + end), span.style()));
            cursor = end;
        }
        list.add(FormattedCharSequence.forward(fullLine.substring(visibleStart + cursor, visibleStart + visibleLength), DEFAULT_STYLE));
        return FormattedCharSequence.composite(list);
    }

    private void collectAliasDefinitionSpans(String fullLine, List<HighlightSpan> spans, int lineNumber) {
        int cursor = 0;
        while (cursor < fullLine.length() && Character.isWhitespace(fullLine.charAt(cursor))) {
            cursor++;
        }

        int keywordStart = cursor;
        if (!fullLine.startsWith("#def", cursor)) {
            spans.add(new HighlightSpan(StringRange.between(0, fullLine.length()), UNPARSED_STYLE));
            return;
        }

        int keywordEnd = cursor + "#def".length();
        spans.add(new HighlightSpan(StringRange.between(keywordStart, keywordEnd), DEFAULT_STYLE));
        cursor = keywordEnd;

        while (cursor < fullLine.length() && Character.isWhitespace(fullLine.charAt(cursor))) {
            cursor++;
        }

        Matcher nameMatcher = ALIAS_NAME_PATTERN.matcher(fullLine);
        nameMatcher.region(cursor, fullLine.length());
        if (nameMatcher.lookingAt()) {
            String name = nameMatcher.group();
            Style nameStyle = ZPSCommands.isMapperName(name) ? UNPARSED_STYLE : GETTER_STYLE;
            spans.add(new HighlightSpan(StringRange.between(nameMatcher.start(), nameMatcher.end()), nameStyle));
            cursor = nameMatcher.end();
        } else if (cursor < fullLine.length()) {
            int invalidStart = cursor;
            while (cursor < fullLine.length() && !Character.isWhitespace(fullLine.charAt(cursor)) && fullLine.charAt(cursor) != '=') {
                cursor++;
            }
            spans.add(new HighlightSpan(StringRange.between(invalidStart, cursor), UNPARSED_STYLE));
        }

        int equals = fullLine.indexOf('=', cursor);
        if (equals == -1) {
            int unexpectedStart = cursor;
            while (unexpectedStart < fullLine.length() && Character.isWhitespace(fullLine.charAt(unexpectedStart))) {
                unexpectedStart++;
            }
            if (unexpectedStart < fullLine.length()) {
                spans.add(new HighlightSpan(StringRange.between(unexpectedStart, fullLine.length()), UNPARSED_STYLE));
            }
            return;
        }

        spans.add(new HighlightSpan(StringRange.between(equals, equals + 1), DEFAULT_STYLE));
        int expressionStart = equals + 1;
        while (expressionStart < fullLine.length() && Character.isWhitespace(fullLine.charAt(expressionStart))) {
            expressionStart++;
        }
        if (expressionStart < fullLine.length()) {
            collectAliasExpressionSpans(fullLine.substring(expressionStart), expressionStart, spans, lineNumber);
        }
    }

    private void collectAliasExpressionSpans(String expression, int offset, List<HighlightSpan> spans, int lineNumber) {
        Map<String, ScriptAliases.AliasDefinition> visibleAliases = aliasesBeforeLine(lineNumber);
        assert this.minecraft.player != null;
        ParseResults<SharedSuggestionProvider> bestParse = null;
        int bestScore = -1;
        for (ResourceLocation typeKey : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            var dispatcher = ValueOfOrLiteralArgumentType.valueOfDispatcherWithExpressionAliases(
                    typeKey,
                    visibleAliases,
                    this.minecraft.player.connection.getSuggestionsProvider()
            );
            if (dispatcher == null) {
                continue;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                var rawDispatcher = (CommandDispatcher) dispatcher;
                ParseResults<SharedSuggestionProvider> parse = rawDispatcher.parse(
                        expression,
                        this.minecraft.player.connection.getSuggestionsProvider()
                );
                int score = parse.getReader().getCursor() * 10 - parse.getExceptions().size();
                if (score > bestScore) {
                    bestScore = score;
                    bestParse = parse;
                }
            } catch (Exception ignored) {
            }
        }

        if (bestParse == null) {
            spans.add(new HighlightSpan(StringRange.between(offset, offset + expression.length()), ARGUMENT_STYLE));
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
            spans.add(new HighlightSpan(StringRange.between(start, offset + expression.length()), UNPARSED_STYLE));
        }
    }

    @Nullable
    static String calculateSuggestionSuffix(String string, String string2) {
        return string2.startsWith(string) ? string2.substring(string.length()) : null;
    }

    private static FormattedCharSequence formatText(
            ParseResults<SharedSuggestionProvider> parseResults,
            String fullText,
            int visibleStart,
            int visibleLength,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        List<FormattedCharSequence> list = Lists.<FormattedCharSequence>newArrayList();
        List<HighlightSpan> spans = new ArrayList<>();
        Set<String> expressionAliasNames = expressionAliases.keySet();
        Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles = buildLiteralStyleIndex(
                parseResults.getContext().getRootNode(),
                expressionAliasNames
        );
        collectLiteralSpans(parseResults.getContext(), spans, literalStyles, expressionAliasNames);
        collectArgumentSpans(parseResults.getContext(), spans, parseResults.getContext().getSource(), expressionAliases);
        spans.sort(Comparator.comparingInt(span -> span.range().getStart()));

        int cursor = 0;
        for (HighlightSpan span : spans) {
            int start = Math.max(span.range().getStart() - visibleStart, 0);
            if (start >= visibleLength) {
                break;
            }

            int end = Math.min(span.range().getEnd() - visibleStart, visibleLength);
            if (end <= start) {
                continue;
            }

            if (start > cursor) {
                list.add(FormattedCharSequence.forward(fullText.substring(visibleStart + cursor, visibleStart + start), DEFAULT_STYLE));
            }

            String spanText = fullText.substring(visibleStart + start, visibleStart + end);
            list.add(FormattedCharSequence.forward(spanText, span.style()));
            cursor = end;
        }

        // Mark any remaining unparsed text as invalid
        if (parseResults.getReader().canRead()) {
            int n = Math.max(parseResults.getReader().getCursor() - visibleStart, 0);
            if (n < visibleLength) {
                list.add(FormattedCharSequence.forward(fullText.substring(visibleStart + cursor, visibleStart + n), DEFAULT_STYLE));
                if (isArgumentPlaceholderAt(fullText, parseResults.getReader().getCursor())) {
                    int o = Math.min(n + ARGUMENT_PLACEHOLDER.length(), visibleLength);
                    list.add(FormattedCharSequence.forward(fullText.substring(visibleStart + n, visibleStart + o), ARGUMENT_STYLE));
                    cursor = o;
                } else {
                    int o = Math.min(n + parseResults.getReader().getRemainingLength(), visibleLength);
                    list.add(FormattedCharSequence.forward(fullText.substring(visibleStart + n, visibleStart + o), UNPARSED_STYLE));
                    cursor = o;
                }
            }
        }

        list.add(FormattedCharSequence.forward(fullText.substring(visibleStart + cursor, visibleStart + visibleLength), DEFAULT_STYLE));
        return FormattedCharSequence.composite(list);
    }

    private static boolean hasArgumentPlaceholder(@Nullable ParseResults<SharedSuggestionProvider> parseResults) {
        if (parseResults == null || !parseResults.getReader().canRead()) {
            return false;
        }

        String input = parseResults.getReader().getString();
        int cursor = parseResults.getReader().getCursor();
        return isArgumentPlaceholderAt(input, cursor);
    }

    private static boolean isArgumentPlaceholderAt(String input, int start) {
        int end = start + ARGUMENT_PLACEHOLDER.length();
        if (start < 0 || end > input.length() || !input.startsWith(ARGUMENT_PLACEHOLDER, start)) {
            return false;
        }

        boolean startsAtBoundary = start == 0 || Character.isWhitespace(input.charAt(start - 1));
        boolean endsAtBoundary = end == input.length() || Character.isWhitespace(input.charAt(end));
        return startsAtBoundary && endsAtBoundary;
    }

    private static void collectLiteralSpans(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<HighlightSpan> spans,
            Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles,
            Set<String> expressionAliasNames
    ) {
        for (ParsedCommandNode<SharedSuggestionProvider> node : context.getNodes()) {
            if (node.getNode() instanceof LiteralCommandNode<?> literalNode) {
                Style style = literalStyles.getOrDefault(node.getNode(), styleForLiteralFallback(literalNode.getLiteral(), expressionAliasNames));
                spans.add(new HighlightSpan(node.getRange(), style));
            }
        }

        if (context.getChild() != null) {
            collectLiteralSpans(context.getChild(), spans, literalStyles, expressionAliasNames);
        }
    }

    private static void collectArgumentSpans(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<HighlightSpan> spans,
            SharedSuggestionProvider source,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        for (ParsedArgument<SharedSuggestionProvider, ?> parsedArgument : context.getArguments().values()) {
            Object result = parsedArgument.getResult();
            if (result instanceof ValueOfExpression<?> expr) {
                collectValueOfSpans(expr, parsedArgument.getRange(), spans, source, expressionAliases);
            } else {
                spans.add(new HighlightSpan(parsedArgument.getRange(), ARGUMENT_STYLE));
            }
        }

        if (context.getChild() != null) {
            collectArgumentSpans(context.getChild(), spans, source, expressionAliases);
        }
    }

    private static void collectValueOfSpans(
            ValueOfExpression<?> expression,
            StringRange range,
            List<HighlightSpan> spans,
            SharedSuggestionProvider source,
            Map<String, ScriptAliases.AliasDefinition> expressionAliases
    ) {
        int start = range.getStart();
        int prefixEnd = Math.min(start + "value_of(".length(), range.getEnd());
        spans.add(new HighlightSpan(StringRange.between(start, prefixEnd), ARGUMENT_STYLE));

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
                spans.add(new HighlightSpan(StringRange.between(prefixEnd, range.getEnd() - 1), ARGUMENT_STYLE));
            }
        } else if (prefixEnd < range.getEnd() - 1) {
            spans.add(new HighlightSpan(StringRange.between(prefixEnd, range.getEnd() - 1), ARGUMENT_STYLE));
        }

        if (range.getEnd() > prefixEnd) {
            spans.add(new HighlightSpan(StringRange.between(range.getEnd() - 1, range.getEnd()), ARGUMENT_STYLE));
        }
    }

    private static void collectLiteralSpansWithOffset(
            CommandContextBuilder<SharedSuggestionProvider> context,
            List<HighlightSpan> spans,
            int offset,
            Map<CommandNode<SharedSuggestionProvider>, Style> literalStyles,
            Set<String> expressionAliasNames
    ) {
        for (ParsedCommandNode<SharedSuggestionProvider> node : context.getNodes()) {
            if (node.getNode() instanceof LiteralCommandNode<?> literalNode) {
                spans.add(new HighlightSpan(
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
            List<HighlightSpan> spans,
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
                spans.add(new HighlightSpan(shiftedRange, ARGUMENT_STYLE));
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

    private static Style styleForLiteralFallback(String literal) {
        return styleForLiteralFallback(literal, Set.of());
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

    private record HighlightSpan(StringRange range, Style style) {
    }

    private record AliasExpressionParse(
            CommandDispatcher<SharedSuggestionProvider> dispatcher,
            ParseResults<SharedSuggestionProvider> parse,
            int expressionStart,
            int expressionCursor
    ) {
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
                this.offset = Mth.clamp(
                        this.current + MultiLineCommandSuggestions.this.lineStartOffset - MultiLineCommandSuggestions.this.suggestionLineLimit,
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
                    ? Component.translatable("narration.suggestion.tooltip", this.current + 1, this.suggestionList.size(), suggestion.getText(), message)
                    : Component.translatable("narration.suggestion", this.current + 1, this.suggestionList.size(), suggestion.getText());
        }
    }

}
