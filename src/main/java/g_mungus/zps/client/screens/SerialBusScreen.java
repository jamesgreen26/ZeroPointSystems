package g_mungus.zps.client.screens;

import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.client.screens.components.MultiLineCommandSuggestions;
import g_mungus.zps.client.screens.components.MultiLineEditBox;
import g_mungus.zps.client.screens.components.ScriptDispatcherProvider;
import g_mungus.zps.client.screens.components.ScriptSyntaxHighlighter;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import g_mungus.zps.networking.SerialBusSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The serial bus's screen: which mode it is in, plus what that mode has to show — in Execute a
 * readout of the last command it ran, in Get the expression it reads the block in front with.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory. Every edit is
 * sent to the bus as it is made, so there is nothing to confirm and nothing to lose by closing.
 * Everything else is drawn from the block entity each frame, so it follows the bus while the
 * screen is open; a failure shows its reason and marks the part of the text it points at.
 */
public class SerialBusScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.serial_bus");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.serial_bus.mode");
    private static final Component LAST_COMMAND_LABEL = Component.translatable("gui.zps.serial_bus.last_command");
    private static final Component NO_COMMAND = Component.translatable("gui.zps.serial_bus.no_command");
    private static final Component EVALUATING_LABEL = Component.translatable("gui.zps.serial_bus.evaluating");
    private static final Component EXPRESSION_LABEL = Component.translatable("gui.zps.serial_bus.expression");
    private static final Component EXPRESSION_HINT = Component.translatable("gui.zps.serial_bus.expression_hint");
    private static final Component NO_BUS = Component.translatable("gui.zps.serial_bus.no_bus");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    private static final int LINE_GAP = 1;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int MAX_COMMAND_LINES = 3;
    private static final Style FAULT_STYLE = Style.EMPTY.withColor(ChatFormatting.RED).withUnderlined(true);
    private static final String ELLIPSIS = "...";
    private static final int BOX_HEIGHT = 18;
    private static final int FAULT_UNDERLINE_COLOUR = 0xFFFF5555;

    /** A Get expression has to read as a chain yielding text: that is what goes on the pipe. */
    private static final ResourceLocation STRING_TYPE = ResourceLocation.parse("zps:string");

    private static final int LABEL_COLOUR = 0xA0A0A0;
    private static final int TEXT_COLOUR = 0xFFFFFF;

    private final BlockPos blockPos;

    /** The mode the player has picked, sent to the bus the moment it changes. */
    private SerialBusMode mode = SerialBusMode.EXECUTE;
    /** Likewise the expression: kept here across a rebuild of the widgets, sent as it is typed. */
    private String expression = "";

    private @Nullable MultiLineEditBox expressionBox;
    private @Nullable MultiLineCommandSuggestions expressionSuggestions;
    /** Set by a mode toggle that lands on Get, for {@link #mouseClicked} to act on once the click is over. */
    private boolean focusExpressionBox;

    private @Nullable String highlightedCommand;
    private List<ScriptSyntaxHighlighter.Span> highlighting = List.of();

    private int titleY;
    private int modeLabelY;
    private int bodyY;
    private boolean initialised;

    public SerialBusScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        SerialBusBlockEntity bus = getBlockEntity();
        // Only the first build reads the bus: afterwards the screen's own copy is what the player
        // has been editing, and a mode toggle rebuilds the widgets around it.
        if (!initialised) {
            mode = bus != null ? bus.getMode() : SerialBusMode.EXECUTE;
            expression = bus != null ? bus.getExpression() : "";
            initialised = true;
        } else if (expressionBox != null) {
            expression = expressionBox.getValue();
        }
        expressionBox = null;
        expressionSuggestions = null;

        // One body height for both modes — the tallest either can need — so the stack holds still
        // as commands of different lengths come and go, and toggling the mode moves nothing but
        // the body itself. Get mode simply leaves the slack below.
        int readoutHeight = LABEL_TO_CONTROL_GAP + MAX_COMMAND_LINES * (this.font.lineHeight + LINE_GAP);
        int bodyHeight = Math.max(readoutHeight, LABEL_TO_CONTROL_GAP + BOX_HEIGHT);
        // The title counts from its own top, so the whole stack — title through body — is what
        // gets centred; the font's line height stands in for the title's own row.
        int contentHeight = this.font.lineHeight + TITLE_TO_FIRST_LABEL_GAP + LABEL_TO_CONTROL_GAP
                + CONTROL_HEIGHT + SECTION_GAP + bodyHeight;
        titleY = Math.max(SECTION_GAP, (this.height - contentHeight) / 2);
        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        bodyY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    focusExpressionBox = mode == SerialBusMode.GET;
                    sendSettings();
                    // The body differs by mode, so the whole stack is laid out again.
                    this.rebuildWidgets();
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        if (mode == SerialBusMode.GET) {
            addExpressionBox(left, bodyY + LABEL_TO_CONTROL_GAP, bus);
        }
    }

    /**
     * One line, because one expression is all the bus evaluates: the box is sized to a single line
     * and refuses newlines outright, so nothing can scroll out of sight above or below.
     */
    private void addExpressionBox(int left, int y, @Nullable SerialBusBlockEntity bus) {
        MultiLineEditBox box = new MultiLineEditBox(this.font, left, y, CONTROL_WIDTH, BOX_HEIGHT, EXPRESSION_HINT);
        box.setMaxLength(SerialBusSettingsC2SPacket.MAX_EXPRESSION_LENGTH);
        box.setFilter(value -> value.indexOf('\n') < 0);
        box.setValue(expression);
        box.setResponder(value -> {
            expression = value;
            sendSettings();
            if (expressionSuggestions != null) {
                expressionSuggestions.updateCommandInfo();
            }
        });
        this.addWidget(box);
        this.setInitialFocus(box);
        expressionBox = box;

        MultiLineCommandSuggestions suggestions = new MultiLineCommandSuggestions(
                this.minecraft, new ScriptDispatcherProvider(this.minecraft), this, box, this.font,
                false, false, 0, 7, false, Integer.MIN_VALUE, facedBlock(bus));
        suggestions.setExpressionType(STRING_TYPE);
        suggestions.setAllowSuggestions(true);
        suggestions.updateCommandInfo();
        expressionSuggestions = suggestions;
    }

    /**
     * The block the bus reads, so the suggestions offer only what that block can answer — the same
     * filtering the script terminal does, for the one bus this screen belongs to rather than every
     * bus on a network. Null when there is no bus to ask, which filters nothing.
     */
    private @Nullable Set<ResourceLocation> facedBlock(@Nullable SerialBusBlockEntity bus) {
        if (bus == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        Block block = this.minecraft.level.getBlockState(bus.getAffectedBlockPos()).getBlock();
        return Set.of(block.builtInRegistryHolder().key().location());
    }

    /** Hands the bus what the player has set. Called for every edit, so there is nothing to apply. */
    private void sendSettings() {
        ZPSGamePackets.sendToServer(new SerialBusSettingsC2SPacket(blockPos, mode, expression));
    }

    @Override
    public void tick() {
        if (expressionBox != null) {
            expressionBox.tick();
        }
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (expressionSuggestions != null && expressionSuggestions.keyPressed(key, scancode, modifiers)) {
            return true;
        }
        // Enter closes. It is checked before the box sees it, because the box would otherwise
        // swallow it trying to start a line the filter will not allow anyway.
        if (key == 257 || key == 335) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public void onClose() {
        // The suggestion machinery keeps what it is completing against in client-wide statics.
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of());
        ValueOfOrLiteralArgumentType.setActiveExpressionAliasNames(Set.of());
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = (expressionSuggestions != null && expressionSuggestions.mouseClicked(mouseX, mouseY, button))
                || super.mouseClicked(mouseX, mouseY, button);
        // Switching to Get rebuilds the widgets from inside the mode button's click, and the new
        // box takes focus then — but vanilla ends the click by focusing whatever was clicked, which
        // is the old button, already gone. So the box is given focus again after the click is done.
        if (focusExpressionBox && expressionBox != null) {
            this.setFocused(expressionBox);
            this.setDragging(false);
        }
        focusExpressionBox = false;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return (expressionSuggestions != null && expressionSuggestions.mouseScrolled(scrollY))
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, TEXT_COLOUR);

        // The label and readout share the mode button's column, so the whole stack lines up.
        int bodyWidth = Math.min(CONTROL_WIDTH, this.width - 2 * SECTION_GAP);
        int left = this.width / 2 - bodyWidth / 2;
        graphics.drawString(this.font, MODE_LABEL, left, modeLabelY, LABEL_COLOUR);

        SerialBusBlockEntity bus = getBlockEntity();
        if (bus == null) {
            graphics.drawString(this.font, NO_BUS, left, bodyY, LABEL_COLOUR);
        } else if (mode == SerialBusMode.GET) {
            // The body follows the mode the player has picked, not the one the bus is still in.
            renderExpressionBody(graphics, bus, left, bodyY, mouseX, mouseY);
        } else {
            renderExecuteReadout(graphics, bus, left, bodyY, bodyWidth, mouseX, mouseY);
        }

        if (expressionSuggestions != null) {
            expressionSuggestions.render(graphics, mouseX, mouseY);
        }
    }

    /**
     * The expression box, with the failure the bus reported for it underlined and explained. The
     * verdict is only shown while it still describes what is in the box: edit the text and it goes.
     */
    private void renderExpressionBody(GuiGraphics graphics, SerialBusBlockEntity bus, int left, int y,
                                      int mouseX, int mouseY) {
        graphics.drawString(this.font, EXPRESSION_LABEL, left, y, LABEL_COLOUR);
        if (expressionBox == null) {
            return;
        }
        expressionBox.render(graphics, mouseX, mouseY, 0.0F);

        ScriptCommandFailure failure = bus.getLastFailure();
        if (failure == null || !bus.getLastCommand().equals(expressionBox.getValue())) {
            return;
        }
        if (failure.faultInCommand()) {
            underlineFault(graphics, failure);
        }
        boolean showingSuggestions = expressionSuggestions != null && expressionSuggestions.isShowingSuggestions();
        if (!showingSuggestions && expressionBox.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, failureTooltip(failure), mouseX, mouseY);
        }
    }

    /**
     * Drawn over the box rather than styled into it: the box scrolls its text sideways and the
     * formatter only ever sees the visible window, which a fault span does not respect.
     */
    private void underlineFault(GuiGraphics graphics, ScriptCommandFailure failure) {
        if (expressionBox == null) {
            return;
        }
        int boxLeft = expressionBox.getX();
        int boxRight = boxLeft + expressionBox.getWidth();
        int startX = Mth.clamp(expressionBox.getScreenX(failure.faultStart()), boxLeft, boxRight);
        int endX = Mth.clamp(expressionBox.getScreenX(failure.faultEnd()), boxLeft, boxRight);
        int baseline = expressionBox.getScreenY(failure.faultStart()) + this.font.lineHeight;
        if (endX > startX) {
            graphics.fill(startX, baseline, endX, baseline + 1, FAULT_UNDERLINE_COLOUR);
        }
    }

    public void renderBackground(GuiGraphics p_333749_, int p_333882_, int p_333946_, float p_334094_) {
        this.renderTransparentBackground(p_333749_);
    }

    private void renderExecuteReadout(GuiGraphics graphics, SerialBusBlockEntity bus, int left, int y, int width,
                                      int mouseX, int mouseY) {
        ScriptCommandFailure failure = bus.getLastFailure();
        graphics.drawString(this.font, LAST_COMMAND_LABEL, left, y, LABEL_COLOUR);
        y += LABEL_TO_CONTROL_GAP;

        if (bus.getLastOutcome() == SerialBusBlockEntity.Outcome.NONE) {
            drawWrapped(graphics, NO_COMMAND, left, y, width, LABEL_COLOUR);
            return;
        }

        boolean marks = failure != null && failure.faultInCommand();
        Component command = trimmedCommand(bus.getLastCommand(), width,
                marks ? failure.faultStart() : 0, marks ? failure.faultEnd() : 0);
        int commandTop = y;
        int commandBottom = drawWrapped(graphics, command, left, y, width, TEXT_COLOUR);

        if (failure == null) return;
        boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= commandTop && mouseY < commandBottom;
        if (hovered) {
            graphics.renderTooltip(this.font, failureTooltip(failure), mouseX, mouseY);
        }
    }

    /** Why the command did not run, wrapped to the tooltip's width. */
    private List<FormattedCharSequence> failureTooltip(ScriptCommandFailure failure) {
        List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(
                Component.literal(failure.reason()), TOOLTIP_WIDTH));
        if (!failure.evaluated().isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(this.font.split(
                    EVALUATING_LABEL.copy().withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            lines.addAll(this.font.split(
                    marked(failure.evaluated(), failure.faultStart(), failure.faultEnd()), TOOLTIP_WIDTH));
        }
        return lines;
    }

    /**
     * The command cut down to {@link #MAX_COMMAND_LINES} lines, with an ellipsis standing in for
     * whatever was dropped. The fault span is what the window is placed around, so a mistake deep
     * in a long command stays on screen even when both ends of it are cut away.
     */
    private Component trimmedCommand(String text, int width, int faultStart, int faultEnd) {
        faultStart = Math.max(0, Math.min(text.length(), faultStart));
        faultEnd = Math.max(faultStart, Math.min(text.length(), faultEnd));

        if (this.font.split(Component.literal(text), width).size() <= MAX_COMMAND_LINES) {
            return windowed(text, 0, text.length(), faultStart, faultEnd);
        }

        List<int[]> lines = wrapRanges(text, width);
        int firstLine = lineOf(lines, faultStart);
        int lastLine = lineOf(lines, Math.max(faultStart, faultEnd - 1));
        int startLine = Math.min(firstLine, Math.max(0, lastLine - (MAX_COMMAND_LINES - 1)));
        startLine = Math.max(0, Math.min(startLine, lines.size() - MAX_COMMAND_LINES));

        int from = lines.get(startLine)[0];
        int to = lines.get(startLine + MAX_COMMAND_LINES - 1)[1];

        // The ellipses need room of their own, so give the window back a character at a time —
        // from whichever end is further from the fault — until the result really does fit.
        while (true) {
            String shown = (from > 0 ? ELLIPSIS : "") + text.substring(from, to)
                    + (to < text.length() ? ELLIPSIS : "");
            if (this.font.split(Component.literal(shown), width).size() <= MAX_COMMAND_LINES
                    || to <= from) {
                return windowed(text, from, to, faultStart, faultEnd);
            }
            if (to > faultEnd || from >= faultStart) {
                to--;
            } else {
                from++;
            }
        }
    }

    /**
     * The window {@code [from, to)} of the command, syntax-highlighted the way the script terminal
     * highlights it, with the elided ends shown as greyed ellipses and the fault span — which the
     * highlighter knows nothing about — picked out on top.
     */
    private Component windowed(String text, int from, int to, int faultStart, int faultEnd) {
        MutableComponent shown = Component.empty();
        if (from > 0) shown.append(ellipsis());
        for (ScriptSyntaxHighlighter.Span span : highlight(text)) {
            int start = Math.max(span.start(), from);
            int end = Math.min(span.end(), to);
            if (end <= start) continue;
            // The fault cuts across the highlighter's spans, so each one is split around it.
            append(shown, text, start, Math.min(end, faultStart), span.style());
            append(shown, text, Math.max(start, faultStart), Math.min(end, faultEnd), FAULT_STYLE);
            append(shown, text, Math.max(start, faultEnd), end, span.style());
        }
        if (to < text.length()) shown.append(ellipsis());
        return shown;
    }

    private static void append(MutableComponent to, String text, int start, int end, Style style) {
        if (end > start) to.append(Component.literal(text.substring(start, end)).withStyle(style));
    }

    /** The command's highlighting, held onto between frames since it costs a parse to work out. */
    private List<ScriptSyntaxHighlighter.Span> highlight(String text) {
        if (!text.equals(highlightedCommand)) {
            highlightedCommand = text;
            highlighting = ScriptSyntaxHighlighter.spans(text);
        }
        return highlighting;
    }

    private static Component ellipsis() {
        // Dimmer than the highlighter's own grey, so it reads as a cut rather than as command text.
        return Component.literal(ELLIPSIS).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Where the wrapped lines start and end in the text, as {@code [start, end)} pairs. */
    private List<int[]> wrapRanges(String text, int width) {
        List<int[]> lines = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            String rest = text.substring(i);
            int len = this.font.plainSubstrByWidth(rest, width).length();
            if (len < rest.length()) {
                int space = rest.lastIndexOf(' ', Math.max(0, len - 1));
                if (space > 0) len = space + 1;
            }
            lines.add(new int[]{i, i + Math.max(1, len)});
            i += Math.max(1, len);
        }
        if (lines.isEmpty()) lines.add(new int[]{0, 0});
        return lines;
    }

    /** The line holding {@code pos}, or the last one if it sits past the end. */
    private static int lineOf(List<int[]> lines, int pos) {
        for (int i = 0; i < lines.size(); i++) {
            if (pos < lines.get(i)[1]) return i;
        }
        return lines.size() - 1;
    }

    /** Draws the text wrapped to the width and returns the y just below its last line. */
    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, colour);
            y += this.font.lineHeight + LINE_GAP;
        }
        return y;
    }

    /** The text with the span from {@code start} to {@code end} picked out as the culprit. */
    private static Component marked(String text, int start, int end) {
        start = Math.max(0, Math.min(text.length(), start));
        end = Math.max(start, Math.min(text.length(), end));
        if (end == start) {
            return Component.literal(text);
        }
        return Component.literal(text.substring(0, start))
                .append(Component.literal(text.substring(start, end))
                        .withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE))
                .append(Component.literal(text.substring(end)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component modeButtonText() {
        return Component.translatable(mode.translationKey());
    }

    private @Nullable SerialBusBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof SerialBusBlockEntity bus)) return null;
        return bus;
    }
}
