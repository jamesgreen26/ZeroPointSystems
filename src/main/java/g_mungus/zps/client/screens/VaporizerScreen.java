package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.menu.VaporizerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Ingredients on the left, then the progress arrow, then the two gauges: Flux buffer and stored
 * energy. Bars are painted empty in the texture and filled here, the same way the Power Cell and
 * Rolling Mill draw theirs.
 */
public class VaporizerScreen extends AbstractContainerScreen<VaporizerMenu> {

    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/vaporizer.png");

    private static final int BAR_WIDTH = 10;
    private static final int BAR_HEIGHT = 50;

    private static final int GAS_BAR_X = 116;
    private static final int GAS_BAR_Y = 18;
    private static final int GAS_COLOR = 0xFF8FD8F0;

    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    private static final int ARROW_X = 76;
    private static final int ARROW_Y = 34;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_TEXTURE_U = 176;
    private static final int ARROW_TEXTURE_V = 14;

    public VaporizerScreen(VaporizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int progress = progressWidth();
        if (progress > 0) {
            graphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y, ARROW_TEXTURE_U, ARROW_TEXTURE_V,
                    progress, ARROW_HEIGHT);
        }

        drawBar(graphics, x + GAS_BAR_X, y + GAS_BAR_Y, fillFor(menu.getBufferPermille(), 1000), GAS_COLOR);
        drawBar(graphics, x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                fillFor(menu.getEnergyStored(), menu.getMaxEnergyStored()), ENERGY_COLOR);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "Flux", GAS_BAR_X - 6, GAS_BAR_Y + BAR_HEIGHT + 4, 0x404040, false);
        graphics.drawString(this.font, "FE", ENERGY_BAR_X, ENERGY_BAR_Y + BAR_HEIGHT + 4, 0x404040, false);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private int progressWidth() {
        int max = menu.getMaxProgress();
        if (max <= 0) {
            return 0;
        }
        return Mth.clamp(menu.getProgress() * ARROW_WIDTH / max, 0, ARROW_WIDTH);
    }

    private int fillFor(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        return Mth.clamp(value * BAR_HEIGHT / max, 0, BAR_HEIGHT);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int fill, int color) {
        if (fill > 0) {
            graphics.fill(x, y + BAR_HEIGHT - fill, x + BAR_WIDTH, y + BAR_HEIGHT, color);
        }
    }
}
