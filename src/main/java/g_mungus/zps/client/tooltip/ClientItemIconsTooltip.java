package g_mungus.zps.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Lays an {@link ItemIconsTooltip}'s items out left to right, wrapping after {@link #PER_ROW}. */
public class ClientItemIconsTooltip implements ClientTooltipComponent {
    private static final int ICON_SIZE = 18;
    private static final int PER_ROW = 9;
    /** Breathing room under the last row, so the icons do not sit on the next tooltip line. */
    private static final int BOTTOM_PADDING = 2;

    private final List<ItemStack> items;

    public ClientItemIconsTooltip(ItemIconsTooltip tooltip) {
        this.items = tooltip.items();
    }

    @Override
    public int getHeight() {
        int rows = (items.size() + PER_ROW - 1) / PER_ROW;
        return rows * ICON_SIZE + BOTTOM_PADDING;
    }

    @Override
    public int getWidth(@NotNull Font font) {
        return Math.min(items.size(), PER_ROW) * ICON_SIZE;
    }

    @Override
    public void renderImage(@NotNull Font font, int x, int y, @NotNull GuiGraphics graphics) {
        for (int i = 0; i < items.size(); i++) {
            int iconX = x + (i % PER_ROW) * ICON_SIZE + 1;
            int iconY = y + (i / PER_ROW) * ICON_SIZE + 1;
            ItemStack stack = items.get(i);
            graphics.renderItem(stack, iconX, iconY);
            graphics.renderItemDecorations(font, stack, iconX, iconY);
        }
    }
}
