package g_mungus.zps.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A checkbox that marks a gas as blocked: the vanilla box, with an X in it instead of a tick when
 * the gas is on the blacklist. Vanilla's {@code Checkbox} hard-codes its tick sprites, so this is
 * its own widget rather than a subclass.
 */
public class GasBlockToggle extends AbstractButton {

    private static final ResourceLocation BOX_SPRITE = ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation BOX_HIGHLIGHTED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");

    private static final int TEXT_GAP = 4;
    /** Inset of the X from the box edge, and how thick its strokes are, in pixels. */
    private static final int CROSS_INSET = 4;
    private static final int CROSS_THICKNESS = 2;
    private static final int CROSS_COLOUR = 0xFFE05050;
    private static final int TEXT_COLOUR = 0xE0E0E0;

    private final Font font;
    private final int boxSize;
    private boolean blocked;
    private final Consumer<Boolean> onToggle;

    public GasBlockToggle(int x, int y, int maxWidth, Component message, Font font,
                          boolean blocked, Consumer<Boolean> onToggle) {
        super(x, y, Math.min(maxWidth, boxSize(font) + TEXT_GAP + font.width(message)), boxSize(font), message);
        this.font = font;
        this.boxSize = boxSize(font);
        this.blocked = blocked;
        this.onToggle = onToggle;
    }

    /** The same box vanilla's checkbox draws: a little taller than a line of text. */
    private static int boxSize(Font font) {
        return font.lineHeight + 8;
    }

    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public void onPress() {
        blocked = !blocked;
        onToggle.accept(blocked);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableDepthTest();
        graphics.setColor(1.0f, 1.0f, 1.0f, this.alpha);
        RenderSystem.enableBlend();
        graphics.blitSprite(isHoveredOrFocused() ? BOX_HIGHLIGHTED_SPRITE : BOX_SPRITE,
                getX(), getY(), boxSize, boxSize);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (blocked) {
            drawCross(graphics);
        }

        int textX = getX() + boxSize + TEXT_GAP;
        int textY = getY() + (boxSize - font.lineHeight) / 2 + 1;
        graphics.drawString(font, getMessage(), textX, textY, TEXT_COLOUR | Math.round(this.alpha * 255.0f) << 24);
    }

    /** Two diagonal strokes across the inside of the box, drawn a pixel at a time. */
    private void drawCross(GuiGraphics graphics) {
        int left = getX() + CROSS_INSET;
        int top = getY() + CROSS_INSET;
        int span = boxSize - 2 * CROSS_INSET;
        for (int i = 0; i < span; i++) {
            for (int t = 0; t < CROSS_THICKNESS; t++) {
                int x = left + i;
                graphics.fill(x, top + i - t, x + 1, top + i - t + 1, CROSS_COLOUR);
                graphics.fill(x, top + span - 1 - i - t, x + 1, top + span - 1 - i - t + 1, CROSS_COLOUR);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
