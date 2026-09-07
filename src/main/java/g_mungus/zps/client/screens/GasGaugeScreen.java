package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity.Bounds;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity.Mode;
import g_mungus.zps.networking.GasGaugeSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Settings for the gas gauge: what it reads, the range it reads over, and what it reads right now.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory, so there is
 * nothing for a menu to hold. The mode button and both bound fields push the whole settings block
 * to the server as they change, the way the creative gas generator's screen does; a pair of bounds
 * that is out of order is flagged here and never sent.
 *
 * <p>The live reading comes from the block entity's synced node state, so it updates at the
 * network's sync rate while the screen is open.
 */
public class GasGaugeScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.gas_gauge");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.gas_gauge.mode");
    private static final Component READING_LABEL = Component.translatable("gui.zps.gas_gauge.reading");
    private static final Component NO_READING = Component.translatable("gui.zps.gas_gauge.no_reading");
    private static final Component BOUNDS_OUT_OF_ORDER = Component.translatable("gui.zps.gas_gauge.bounds_out_of_order");

    /** What a bound field will take: a plain or exponent-form decimal, as it is being typed. */
    private static final Pattern NUMBER_IN_PROGRESS = Pattern.compile("[0-9]*\\.?[0-9]*([eE][+-]?[0-9]*)?");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int FIELD_GAP = 8;
    private static final int FIELD_WIDTH = (CONTROL_WIDTH - FIELD_GAP) / 2;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int SECTION_GAP = 12;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int BAR_HEIGHT = 6;
    /** Half the stack's height, so the whole thing sits centred on the screen. */
    private static final int TOP_SECTION_Y_OFFSET = -78;

    private static final int LABEL_COLOUR = 0xA0A0A0;
    private static final int ERROR_COLOUR = 0xFF5555;
    private static final int BAR_BACKGROUND = 0xFF202020;
    private static final int BAR_FILL = 0xFFC43028;
    private static final int BAR_BORDER = 0xFF6A6A6A;

    private final BlockPos blockPos;

    private Mode mode = Mode.PRESSURE;
    private EditBox lowerField;
    private EditBox upperField;
    /** Set while the fields are being refilled from the block, so the responders stay quiet. */
    private boolean refilling;

    // Laid out in init(), read back in render() so labels track their controls.
    private int titleY;
    private int modeLabelY;
    private int boundsLabelY;
    private int errorY;
    private int readingY;
    private int barY;

    public GasGaugeScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        GasGaugeBlockEntity gauge = getBlockEntity();
        mode = gauge != null ? gauge.getMode() : Mode.PRESSURE;

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        int centerY = this.height / 2;

        titleY = centerY + TOP_SECTION_Y_OFFSET;
        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        boundsLabelY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;
        int fieldY = boundsLabelY + LABEL_TO_CONTROL_GAP;
        errorY = fieldY + CONTROL_HEIGHT + 4;
        readingY = errorY + SECTION_GAP;
        barY = readingY + LABEL_TO_CONTROL_GAP + 2;

        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    button.setMessage(modeButtonText());
                    refillBounds();
                    sendSettings();
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        this.lowerField = boundField(left, fieldY, Component.translatable("gui.zps.gas_gauge.lower"));
        this.upperField = boundField(left + FIELD_WIDTH + FIELD_GAP, fieldY,
                Component.translatable("gui.zps.gas_gauge.upper"));
        this.addRenderableWidget(lowerField);
        this.addRenderableWidget(upperField);
        refillBounds();
    }

    private EditBox boundField(int x, int y, Component name) {
        EditBox field = new EditBox(this.font, x, y, FIELD_WIDTH, CONTROL_HEIGHT, name);
        field.setMaxLength(24);
        field.setFilter(text -> NUMBER_IN_PROGRESS.matcher(text).matches());
        field.setResponder(text -> {
            if (!refilling) {
                sendSettings();
            }
        });
        return field;
    }

    /** Put the current mode's stored bounds in the fields, without treating that as an edit. */
    private void refillBounds() {
        GasGaugeBlockEntity gauge = getBlockEntity();
        Bounds bounds = gauge != null
                ? gauge.getBounds(mode)
                : new Bounds(mode.defaultLower(), mode.defaultUpper());
        refilling = true;
        try {
            lowerField.setValue(plain(bounds.lower()));
            upperField.setValue(plain(bounds.upper()));
        } finally {
            refilling = false;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - CONTROL_WIDTH / 2;

        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, 0xFFFFFF);
        graphics.drawString(this.font, MODE_LABEL, left, modeLabelY, LABEL_COLOUR);
        graphics.drawString(this.font, boundsLabel("lower"), left, boundsLabelY, LABEL_COLOUR);
        graphics.drawString(this.font, boundsLabel("upper"), left + FIELD_WIDTH + FIELD_GAP, boundsLabelY, LABEL_COLOUR);

        if (parsedBounds() == null) {
            graphics.drawString(this.font, BOUNDS_OUT_OF_ORDER, left, errorY, ERROR_COLOUR);
        }

        GasGaugeBlockEntity gauge = getBlockEntity();
        if (gauge == null) {
            graphics.drawString(this.font, NO_READING, left, readingY, LABEL_COLOUR);
            return;
        }

        double value = gauge.getMeasuredValue();
        double fraction = gauge.getFraction();
        Component reading = Component.translatable("gui.zps.gas_gauge.reading_value",
                READING_LABEL, gauge.getMode().format(value));
        graphics.drawString(this.font, reading, left, readingY, 0xFFFFFF);

        // The dial in miniature: how far along the range the needle is sitting.
        int fill = (int) Math.round(fraction * (CONTROL_WIDTH - 2));
        graphics.fill(left, barY, left + CONTROL_WIDTH, barY + BAR_HEIGHT, BAR_BORDER);
        graphics.fill(left + 1, barY + 1, left + CONTROL_WIDTH - 1, barY + BAR_HEIGHT - 1, BAR_BACKGROUND);
        if (fill > 0) {
            graphics.fill(left + 1, barY + 1, left + 1 + fill, barY + BAR_HEIGHT - 1, BAR_FILL);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendSettings() {
        Bounds bounds = parsedBounds();
        if (bounds == null) {
            return;
        }
        ZPSGamePackets.sendToServer(new GasGaugeSettingsC2SPacket(blockPos, mode, bounds.lower(), bounds.upper()));
    }

    /** What the fields currently say, or null if it is not a range the gauge would accept. */
    private @Nullable Bounds parsedBounds() {
        Double lower = parse(lowerField.getValue());
        Double upper = parse(upperField.getValue());
        if (lower == null || upper == null || !GasGaugeBlockEntity.acceptableBounds(mode, lower, upper)) {
            return null;
        }
        return new Bounds(lower, upper);
    }

    private static @Nullable Double parse(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A bound as it should appear in its field: no exponent, no trailing zeros. */
    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private Component modeButtonText() {
        return Component.translatable(mode.translationKey());
    }

    private Component boundsLabel(String which) {
        return Component.translatable("gui.zps.gas_gauge." + which + "_with_unit", mode.unit());
    }

    private @Nullable GasGaugeBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof GasGaugeBlockEntity gauge)) return null;
        return gauge;
    }
}
