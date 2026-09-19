package g_mungus.zps.client.screens;

import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.networking.SerialBusSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The serial bus's screen: which mode it is in, and in Execute mode a live readout of the last
 * command it ran and how that went.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory. The mode button
 * sends straight to the server, the way the reactor port's does. The readout is drawn from the
 * block entity each frame, so it follows the bus while the screen is open; a failure shows its
 * reason and marks the part of the command it points at.
 */
public class SerialBusScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.serial_bus");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.serial_bus.mode");
    private static final Component LAST_COMMAND_LABEL = Component.translatable("gui.zps.serial_bus.last_command");
    private static final Component NO_COMMAND = Component.translatable("gui.zps.serial_bus.no_command");
    private static final Component EVALUATING_LABEL = Component.translatable("gui.zps.serial_bus.evaluating");
    private static final Component GET_UNAVAILABLE = Component.translatable("gui.zps.serial_bus.get_unavailable");
    private static final Component NO_BUS = Component.translatable("gui.zps.serial_bus.no_bus");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    private static final int LINE_GAP = 1;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int MAX_COMMAND_LINES = 3;
    private static final String ELLIPSIS = "...";

    private static final int LABEL_COLOUR = 0xA0A0A0;
    private static final int TEXT_COLOUR = 0xFFFFFF;

    private final BlockPos blockPos;

    /** The mode as last sent; mirrored from the block when the screen opens. */
    private SerialBusMode mode = SerialBusMode.EXECUTE;

    private int titleY;
    private int modeLabelY;
    private int bodyY;

    public SerialBusScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        SerialBusBlockEntity bus = getBlockEntity();
        mode = bus != null ? bus.getMode() : SerialBusMode.EXECUTE;

        // Centred on the tallest the stack can get — title, mode, and a full-height readout — so
        // the layout holds still as commands of different lengths come and go.
        int bodyHeight = LABEL_TO_CONTROL_GAP + MAX_COMMAND_LINES * (this.font.lineHeight + LINE_GAP);
        int contentHeight = TITLE_TO_FIRST_LABEL_GAP + LABEL_TO_CONTROL_GAP
                + CONTROL_HEIGHT + SECTION_GAP + bodyHeight;
        titleY = Math.max(SECTION_GAP, (this.height - contentHeight) / 2);
        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        bodyY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    button.setMessage(modeButtonText());
                    ZPSGamePackets.sendToServer(new SerialBusSettingsC2SPacket(blockPos, mode));
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
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
        } else if (bus.getMode() == SerialBusMode.GET) {
            drawWrapped(graphics, GET_UNAVAILABLE, left, bodyY, bodyWidth, LABEL_COLOUR);
        } else {
            renderExecuteReadout(graphics, bus, left, bodyY, bodyWidth, mouseX, mouseY);
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
        if (this.font.split(Component.literal(text), width).size() <= MAX_COMMAND_LINES) {
            return marked(text, faultStart, faultEnd);
        }

        faultStart = Math.max(0, Math.min(text.length(), faultStart));
        faultEnd = Math.max(faultStart, Math.min(text.length(), faultEnd));

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
                return windowed(text, from, to, faultStart - from, faultEnd - from);
            }
            if (to > faultEnd || from >= faultStart) {
                to--;
            } else {
                from++;
            }
        }
    }

    /** The window {@code [from, to)} of the command, with the elided ends shown as greyed ellipses. */
    private static Component windowed(String text, int from, int to, int faultStart, int faultEnd) {
        MutableComponent shown = Component.empty();
        if (from > 0) shown.append(ellipsis());
        shown.append(marked(text.substring(from, to), faultStart, faultEnd));
        if (to < text.length()) shown.append(ellipsis());
        return shown;
    }

    private static Component ellipsis() {
        return Component.literal(ELLIPSIS).withStyle(ChatFormatting.GRAY);
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
