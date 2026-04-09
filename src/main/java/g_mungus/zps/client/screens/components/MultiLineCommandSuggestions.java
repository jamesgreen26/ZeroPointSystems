package g_mungus.zps.client.screens.components;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;

import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api_impl.ValueOfDispatchers;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
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
    private static final Style UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    private static final Style LITERAL_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final List<Style> ARGUMENT_STYLES = (List<Style>)Stream.of(
                    ChatFormatting.AQUA, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD
            )
            .map(Style.EMPTY::withColor)
            .collect(ImmutableList.toImmutableList());
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

                // Calculate Y position based on current line
                int currentLineNumber = getCurrentLineNumber();
                int lineHeight = 10; // LINE_HEIGHT from MultiLineEditBox
                int inputY = this.input.getY();
                int lineY = inputY + 4 + (currentLineNumber * lineHeight); // +4 for border
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

            Set<ResourceLocation> associatedBlocks = null;

            ScriptExecutor<?,?> executor = ZPSCommands.getExecutor(command);
            if (executor != null) {
                associatedBlocks = executor.associatedBlocks();
            }

            ScriptGetter<?> getter = ZPSCommands.getGetter(command);
            if (getter != null) {
                associatedBlocks = getter.associatedBlocks();
            }

            if (
                associatedBlocks == null ||
                associatedBlocks.stream().anyMatch(connectedBlocks::contains)
            ) {
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

        if (this.currentParse != null && !this.currentParse.getReader().getString().equals(currentLine)) {
            this.currentParse = null;
        }

        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }

        this.commandUsage.clear();
        StringReader stringReader = new StringReader(currentLine);
        boolean bl = stringReader.canRead() && stringReader.peek() == '/';
        if (bl) {
            stringReader.skip();
        }

        boolean bl2 = this.commandsOnly || bl;
        if (bl2) {
            CommandDispatcher<SharedSuggestionProvider> commandDispatcher = dispatcherProvider.get();
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

        if (lineCursorPos == currentLine.length()) {
            if (((Suggestions)this.pendingSuggestions.join()).isEmpty() && !this.currentParse.getExceptions().isEmpty()) {
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
            } else if (this.currentParse.getReader().canRead()) {
                bl = true;
            }
        }

        this.commandUsagePosition = 0;
        this.commandUsageWidth = this.screen.width;
        if (this.commandUsage.isEmpty() && !this.fillNodeUsage(ChatFormatting.GRAY) && bl) {
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
                dispatcherProvider.get()
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



    private FormattedCharSequence formatChat(String string, int i) {
        // 'string' is the visible portion of a line (after horizontal scrolling)
        // 'i' is the horizontal scroll offset within that line (displayPos)
        // We need to find the full line to parse it correctly

        String fullValue = this.input.getValue();
        String[] lines = fullValue.split("\n", -1);

        // Find which line this visible portion belongs to
        // by checking if line.substring(i) starts with 'string'
        String fullLine = string; // fallback to visible string if we can't find the line

        for (String line : lines) {
            if (i <= line.length() && line.substring(Math.min(i, line.length())).startsWith(string)) {
                fullLine = line;
                break;
            }
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
                CommandDispatcher<SharedSuggestionProvider> commandDispatcher = dispatcherProvider.get();
                ParseResults<SharedSuggestionProvider> lineParseResults = commandDispatcher.parse(stringReader, this.minecraft.player.connection.getSuggestionsProvider());

                return formatText(lineParseResults, string, i, 0);
            } catch (Exception e) {
                // If parsing fails, return unformatted
                return FormattedCharSequence.forward(string, Style.EMPTY);
            }
        }
        return FormattedCharSequence.forward(string, Style.EMPTY);
    }

    @Nullable
    static String calculateSuggestionSuffix(String string, String string2) {
        return string2.startsWith(string) ? string2.substring(string.length()) : null;
    }

    private static FormattedCharSequence formatText(ParseResults<SharedSuggestionProvider> parseResults, String string, int i, int styleOffset) {
        List<FormattedCharSequence> list = Lists.<FormattedCharSequence>newArrayList();
        int j = 0;
        int k = styleOffset - 1;
        CommandContextBuilder<SharedSuggestionProvider> commandContextBuilder = parseResults.getContext().getLastChild();

        for (ParsedArgument<SharedSuggestionProvider, ?> parsedArgument : commandContextBuilder.getArguments().values()) {
            if (++k >= ARGUMENT_STYLES.size()) {
                k = 0;
            }

            int l = Math.max(parsedArgument.getRange().getStart() - i, 0);
            if (l >= string.length()) {
                break;
            }

            int m = Math.min(parsedArgument.getRange().getEnd() - i, string.length());
            if (m > 0) {
                list.add(FormattedCharSequence.forward(string.substring(j, l), LITERAL_STYLE));
                String argumentText = string.substring(l, m);
                if (parsedArgument.getResult() instanceof ValueOfExpression<?> expr && argumentText.startsWith("value_of(") && argumentText.endsWith(")")) {
                    list.add(formatValueOfExpression(expr, argumentText, parseResults.getContext().getSource(), k + 1));
                } else {
                    list.add(FormattedCharSequence.forward(argumentText, (Style)ARGUMENT_STYLES.get(k)));
                }
                j = m;
            }
        }

        // Mark any remaining unparsed text as invalid
        if (parseResults.getReader().canRead()) {
            int n = Math.max(parseResults.getReader().getCursor() - i, 0);
            if (n < string.length()) {
                int o = Math.min(n + parseResults.getReader().getRemainingLength(), string.length());
                list.add(FormattedCharSequence.forward(string.substring(j, n), LITERAL_STYLE));
                list.add(FormattedCharSequence.forward(string.substring(n, o), UNPARSED_STYLE));
                j = o;
            }
        }

        list.add(FormattedCharSequence.forward(string.substring(j), LITERAL_STYLE));
        return FormattedCharSequence.composite(list);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static FormattedCharSequence formatValueOfExpression(
            ValueOfExpression<?> expression,
            String text,
            SharedSuggestionProvider source,
            int styleOffset
    ) {
        List<FormattedCharSequence> parts = Lists.newArrayList();
        Style wrapperStyle = ARGUMENT_STYLES.get(0);
        parts.add(FormattedCharSequence.forward("value_of(", wrapperStyle));

        var innerDispatcher = ValueOfDispatchers.get(expression.targetTypeKey());
        if (innerDispatcher != null) {
            try {
                var rawDispatcher = (CommandDispatcher) innerDispatcher;
                ParseResults<SharedSuggestionProvider> innerParse = rawDispatcher.parse(expression.innerExpression(), source);
                parts.add(formatText(innerParse, expression.innerExpression(), 0, styleOffset));
            } catch (Exception ignored) {
                parts.add(FormattedCharSequence.forward(expression.innerExpression(), ARGUMENT_STYLES.get(styleOffset % ARGUMENT_STYLES.size())));
            }
        } else {
            parts.add(FormattedCharSequence.forward(expression.innerExpression(), ARGUMENT_STYLES.get(styleOffset % ARGUMENT_STYLES.size())));
        }

        if (text.endsWith(")")) {
            parts.add(FormattedCharSequence.forward(")", wrapperStyle));
        }
        return FormattedCharSequence.composite(parts);
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
        // Calculate Y position based on current line
        int currentLineNumber = getCurrentLineNumber();
        int lineHeight = 10; // LINE_HEIGHT from MultiLineEditBox
        int inputY = this.input.getY();
        int lineY = inputY + 4 + (currentLineNumber * lineHeight); // +4 for border
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
