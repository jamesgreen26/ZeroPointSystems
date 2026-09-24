package g_mungus.zps.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The reactor port's gas blacklist: a framed, scrolling list with a row for every gas anyone has
 * registered, each with a box that carries an X when the gas is held back.
 *
 * <p>A list rather than a grid of checkboxes because the gases come from Kelvin's open registry —
 * a pack can add any number of them, so the section has to stay one size and say, by its shape,
 * that its contents are whatever happens to be registered.
 */
public class GasFilterList extends ObjectSelectionList<GasFilterList.Row> {

    /** What the list needs from the screen that owns the filter. */
    public interface Host {
        boolean isBlocked(ResourceLocation gas);

        /** True when the filter is full, so nothing more may be blocked. */
        boolean atLimit();

        void toggle(ResourceLocation gas);
    }

    /**
     * One gas as the list shows it.
     *
     * @param registered false for an id the filter still names but no loaded mod registers
     */
    public record Gas(ResourceLocation id, String name, ResourceLocation icon, boolean registered) {
        boolean matches(String query) {
            return name.toLowerCase(Locale.ROOT).contains(query) || id.toString().contains(query);
        }
    }

    public static final int ROW_HEIGHT = 20;
    /** Vanilla starts its rows this far below the top of the list. */
    private static final int TOP_PADDING = 4;

    /** Vanilla's checkbox sheet: 64x64, four 20px cells, the right-hand column highlighted. */
    private static final ResourceLocation CHECKBOX_SHEET = ResourceLocation.withDefaultNamespace("textures/gui/checkbox.png");
    private static final int CHECKBOX_CELL = 20;

    private static final Component UNREGISTERED = Component.translatable("gui.zps.reactor_port.filter.unregistered");
    private static final Component LIMIT = Component.translatable("gui.zps.reactor_port.filter.limit");

    // The manual's panel colours, so the mod's framed areas match.
    private static final int PANEL_COLOUR = 0xE6141414;
    private static final int PANEL_BORDER = 0xFF343434;
    private static final int HOVER_COLOUR = 0x18FFFFFF;
    private static final int FOCUS_COLOUR = 0xFFA0A0A0;

    /** Vanilla's scrollbar is this wide; it has no constant for it in this version. */
    private static final int SCROLLBAR_WIDTH = 6;

    private static final int BOX_SIZE = 17;
    private static final int ICON_SIZE = 16;
    private static final int ELEMENT_GAP = 4;
    /** Inset of the X from the box edge, and how thick its strokes are, in pixels. */
    private static final int CROSS_INSET = 4;
    private static final int CROSS_THICKNESS = 2;
    private static final int CROSS_COLOUR = 0xFFE05050;

    private static final int TEXT_COLOUR = 0xFFE0E0E0;
    private static final int BLOCKED_TEXT_COLOUR = 0xFFE05050;
    private static final int UNREGISTERED_TEXT_COLOUR = 0xFF808080;
    private static final int MESSAGE_COLOUR = 0xFFA0A0A0;

    // The script terminal's scrollbar: a flat track and handle, the handle white while in use.
    private static final int SCROLLBAR_TRACK_COLOUR = 0xFF222222;
    private static final int SCROLLBAR_HANDLE_COLOUR = 0xFFA0A0A0;
    private static final int SCROLLBAR_HANDLE_ACTIVE_COLOUR = 0xFFFFFFFF;
    private static final String ELLIPSIS = "...";

    private final Host host;
    private final Font font;
    private Component emptyMessage = Component.empty();
    private boolean draggingScrollbar;

    public GasFilterList(Minecraft minecraft, int x, int y, int width, int visibleRows, Host host) {
        super(minecraft, width, heightFor(visibleRows), y, y + heightFor(visibleRows), ROW_HEIGHT);
        this.setLeftPos(x);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        this.host = host;
        this.font = minecraft.font;
    }

    /** The height that shows exactly this many rows. */
    public static int heightFor(int visibleRows) {
        return visibleRows * ROW_HEIGHT + TOP_PADDING;
    }

    /** Show the gases that match the query, in the order given; an empty query shows them all. */
    public void show(List<Gas> gases, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        for (Gas gas : gases) {
            if (needle.isEmpty() || gas.matches(needle)) {
                rows.add(new Row(gas));
            }
        }
        this.replaceEntries(rows);
        this.setScrollAmount(0);
    }

    /** What to say in the middle of the panel when there are no rows. */
    public void setEmptyMessage(Component emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    // --- geometry ---------------------------------------------------------------------------

    /** Rows span the list, so the whole row is the click target; the scrollbar sits over its end. */
    @Override
    public int getRowWidth() {
        return this.width;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - SCROLLBAR_WIDTH - 1;
    }

    private int getX() {
        return this.x0;
    }

    private int getY() {
        return this.y0;
    }


    private boolean hasScrollbar() {
        return this.getMaxScroll() > 0;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return hasScrollbar() && this.isMouseOver(mouseX, mouseY) && mouseX >= this.getScrollbarPosition();
    }

    /** Where row contents have to stop: short of the scrollbar when there is one. */
    private int contentRight() {
        return hasScrollbar() ? this.getScrollbarPosition() - 2 : this.getRight() - 3;
    }

    /** A row is not under the cursor while the scrollbar is: over the bar, or mid-drag off it. */
    private boolean rowsHoverable(double mouseX, double mouseY) {
        return !draggingScrollbar && !isOverScrollbar(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        draggingScrollbar = button == 0 && isOverScrollbar(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- look -------------------------------------------------------------------------------

    /**
     * The panel goes under vanilla's pass and the frame over it. Vanilla draws its own flat
     * scrollbar whenever there is anything to scroll; {@link #renderDecorations} runs after it and
     * paints ours over the same strip.
     */
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), PANEL_COLOUR);
        if (this.getItemCount() == 0) {
            graphics.drawCenteredString(font, emptyMessage, this.getX() + this.width / 2,
                    this.getY() + (this.height - font.lineHeight) / 2, MESSAGE_COLOUR);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, PANEL_BORDER);
    }

    /**
     * The scrollbar, drawn flat like the script terminal's. The handle is sized and placed by
     * vanilla's own sums, because vanilla still does the dragging and the two have to agree.
     */
    @Override
    protected void renderDecorations(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasScrollbar()) return;
        int barLeft = this.getScrollbarPosition();
        int barRight = barLeft + SCROLLBAR_WIDTH;
        // The whole strip, so that vanilla's own bar underneath is painted over; the frame goes on after.
        int trackTop = this.getY();
        int trackBottom = this.getBottom();

        int handleHeight = Mth.clamp((int) ((float) (this.height * this.height) / (float) this.getMaxPosition()),
                32, this.height - 8);
        int handleTop = (int) this.getScrollAmount() * (this.height - handleHeight) / this.getMaxScroll() + this.getY();
        handleTop = Mth.clamp(handleTop, trackTop, trackBottom - handleHeight);

        boolean active = draggingScrollbar || isOverScrollbar(mouseX, mouseY);
        graphics.fill(barLeft, trackTop, barRight, trackBottom, SCROLLBAR_TRACK_COLOUR);
        graphics.fill(barLeft, handleTop, barRight, handleTop + handleHeight,
                active ? SCROLLBAR_HANDLE_ACTIVE_COLOUR : SCROLLBAR_HANDLE_COLOUR);
    }

    /** The outline is for finding your place with the keyboard; a mouse has the hover fill. */
    @Override
    protected boolean isSelectedItem(int index) {
        return this.isFocused() && this.minecraft.getLastInputType().isKeyboard() && super.isSelectedItem(index);
    }

    @Override
    protected void renderSelection(@NotNull GuiGraphics graphics, int top, int width, int height,
                                   int outerColour, int innerColour) {
        graphics.renderOutline(this.getX() + 1, top - 2, contentRight() + 1 - this.getX(), ROW_HEIGHT, FOCUS_COLOUR);
    }

    // --- rows -------------------------------------------------------------------------------

    public class Row extends ObjectSelectionList.Entry<Row> {

        private final Gas gas;

        Row(Gas gas) {
            this.gas = gas;
        }

        /** Blocking is refused once the filter is full; unblocking never is. */
        private boolean refused() {
            return !host.isBlocked(gas.id()) && host.atLimit();
        }

        private void toggle() {
            if (refused()) return;
            host.toggle(gas.id());
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // The scrollbar lies over the end of the row: a click there is a scroll, not a toggle.
            if (isOverScrollbar(mouseX, mouseY)) return false;
            toggle();
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (CommonInputs.selected(keyCode)) {
                toggle();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void renderBack(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering && rowsHoverable(mouseX, mouseY)) {
                graphics.fill(getX() + 1, top - 2, contentRight() + 1, top - 2 + ROW_HEIGHT, HOVER_COLOUR);
            }
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean blocked = host.isBlocked(gas.id());
            hovering = hovering && rowsHoverable(mouseX, mouseY);

            // Everything is centred on the row's 20px slot, which starts 2px above `top`.
            int slotTop = top - 2;
            int boxX = left + 1;
            int boxY = slotTop + (ROW_HEIGHT - BOX_SIZE) / 2;
            RenderSystem.enableBlend();
            graphics.blit(CHECKBOX_SHEET, boxX, boxY, BOX_SIZE, BOX_SIZE,
                    hovering ? CHECKBOX_CELL : 0, 0, CHECKBOX_CELL, CHECKBOX_CELL, 64, 64);
            if (blocked) {
                drawCross(graphics, boxX, boxY);
            }

            int iconX = boxX + BOX_SIZE + ELEMENT_GAP;
            int iconY = slotTop + (ROW_HEIGHT - ICON_SIZE) / 2;
            // Kelvin's icons are whole textures of no fixed size, so stretch the full image.
            if (!gas.registered()) graphics.setColor(1.0f, 1.0f, 1.0f, 0.4f);
            graphics.blit(gas.icon(), iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, 16, 16, 16, 16);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

            int textX = iconX + ICON_SIZE + ELEMENT_GAP;
            int textY = slotTop + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            int colour = !gas.registered() ? UNREGISTERED_TEXT_COLOUR : blocked ? BLOCKED_TEXT_COLOUR : TEXT_COLOUR;
            graphics.drawString(font, fitted(gas.name(), contentRight() - textX), textX, textY, colour);

            if (hovering && minecraft.screen != null) {
                minecraft.screen.setTooltipForNextRenderPass(tooltip());
            }
        }

        private String fitted(String text, int maxWidth) {
            if (font.width(text) <= maxWidth) return text;
            return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
        }

        /** The full name, then the id — which is what shows the gas came out of a registry. */
        private List<FormattedCharSequence> tooltip() {
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (gas.registered()) {
                lines.add(Component.literal(gas.name()).getVisualOrderText());
            }
            lines.add(Component.literal(gas.id().toString()).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            if (!gas.registered()) {
                lines.add(UNREGISTERED.copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            }
            if (refused()) {
                lines.add(LIMIT.copy().withStyle(ChatFormatting.RED).getVisualOrderText());
            }
            return lines;
        }

        /** Two diagonal strokes across the inside of the box, drawn a pixel at a time. */
        private void drawCross(GuiGraphics graphics, int boxX, int boxY) {
            int crossLeft = boxX + CROSS_INSET;
            int crossTop = boxY + CROSS_INSET;
            int span = BOX_SIZE - 2 * CROSS_INSET;
            for (int i = 0; i < span; i++) {
                for (int t = 0; t < CROSS_THICKNESS; t++) {
                    int x = crossLeft + i;
                    graphics.fill(x, crossTop + i - t, x + 1, crossTop + i - t + 1, CROSS_COLOUR);
                    graphics.fill(x, crossTop + span - 1 - i - t, x + 1, crossTop + span - i - t, CROSS_COLOUR);
                }
            }
        }

        @Override
        public @NotNull Component getNarration() {
            Component name = Component.literal(gas.name());
            return host.isBlocked(gas.id())
                    ? Component.translatable("gui.zps.reactor_port.filter.narration.blocked", name)
                    : Component.translatable("gui.zps.reactor_port.filter.narration.passing", name);
        }
    }
}
