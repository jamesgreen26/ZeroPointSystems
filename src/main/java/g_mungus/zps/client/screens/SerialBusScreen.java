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
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private static final Component RESULT_LABEL = Component.translatable("gui.zps.serial_bus.result");
    private static final Component SUCCEEDED = Component.translatable("gui.zps.serial_bus.result.success");
    private static final Component FAILED = Component.translatable("gui.zps.serial_bus.result.failure");
    private static final Component EVALUATING_LABEL = Component.translatable("gui.zps.serial_bus.evaluating");
    private static final Component GET_UNAVAILABLE = Component.translatable("gui.zps.serial_bus.get_unavailable");
    private static final Component NO_BUS = Component.translatable("gui.zps.serial_bus.no_bus");

    private static final int CONTROL_WIDTH = 200;
    private static final int BODY_WIDTH = 260;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    private static final int LINE_GAP = 1;

    private static final int LABEL_COLOUR = 0xA0A0A0;
    private static final int TEXT_COLOUR = 0xFFFFFF;
    private static final int SUCCESS_COLOUR = 0x55FF55;
    private static final int FAILURE_COLOUR = 0xFF5555;

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

        // The readout below the button grows and shrinks with the command, so the stack is
        // anchored near the top rather than centred.
        titleY = Math.max(SECTION_GAP, this.height / 4 - CONTROL_HEIGHT);
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

        int controlLeft = this.width / 2 - CONTROL_WIDTH / 2;
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, TEXT_COLOUR);
        graphics.drawString(this.font, MODE_LABEL, controlLeft, modeLabelY, LABEL_COLOUR);

        int bodyWidth = Math.min(BODY_WIDTH, this.width - 2 * SECTION_GAP);
        int left = this.width / 2 - bodyWidth / 2;
        SerialBusBlockEntity bus = getBlockEntity();
        if (bus == null) {
            graphics.drawString(this.font, NO_BUS, left, bodyY, LABEL_COLOUR);
        } else if (bus.getMode() == SerialBusMode.GET) {
            drawWrapped(graphics, GET_UNAVAILABLE, left, bodyY, bodyWidth, LABEL_COLOUR);
        } else {
            renderExecuteReadout(graphics, bus, left, bodyY, bodyWidth);
        }
    }

    public void renderBackground(GuiGraphics p_333749_, int p_333882_, int p_333946_, float p_334094_) {
        this.renderTransparentBackground(p_333749_);
    }

    private void renderExecuteReadout(GuiGraphics graphics, SerialBusBlockEntity bus, int left, int y, int width) {
        graphics.drawString(this.font, LAST_COMMAND_LABEL, left, y, LABEL_COLOUR);
        y += LABEL_TO_CONTROL_GAP;

        if (bus.getLastOutcome() == SerialBusBlockEntity.Outcome.NONE) {
            drawWrapped(graphics, NO_COMMAND, left, y, width, LABEL_COLOUR);
            return;
        }

        ScriptCommandFailure failure = bus.getLastFailure();
        Component command = failure != null && failure.faultInCommand()
                ? marked(bus.getLastCommand(), failure.faultStart(), failure.faultEnd())
                : Component.literal(bus.getLastCommand());
        y = drawWrapped(graphics, command, left, y, width, TEXT_COLOUR) + SECTION_GAP;

        graphics.drawString(this.font, RESULT_LABEL, left, y, LABEL_COLOUR);
        y += LABEL_TO_CONTROL_GAP;
        if (failure == null) {
            drawWrapped(graphics, SUCCEEDED, left, y, width, SUCCESS_COLOUR);
            return;
        }
        y = drawWrapped(graphics, FAILED, left, y, width, FAILURE_COLOUR);
        y = drawWrapped(graphics, Component.literal(failure.reason()), left, y, width, TEXT_COLOUR);

        if (!failure.evaluated().isEmpty()) {
            y += SECTION_GAP;
            graphics.drawString(this.font, EVALUATING_LABEL, left, y, LABEL_COLOUR);
            y += LABEL_TO_CONTROL_GAP;
            drawWrapped(graphics, marked(failure.evaluated(), failure.faultStart(), failure.faultEnd()),
                    left, y, width, TEXT_COLOUR);
        }
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
