package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ItemChargingPowerCell;
import g_mungus.zps.menu.PowerCellMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class PowerCellScreen extends AbstractContainerScreen<PowerCellMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/power_cell.png");
    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_BAR_WIDTH = 10;
    private static final int ENERGY_BAR_HEIGHT = 50;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    public PowerCellScreen(PowerCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.literal(formatEnergy(menu.getEnergyStored(), menu.getMaxEnergyStored())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        drawBar(graphics, x + ENERGY_BAR_X, y + ENERGY_BAR_Y, getCellEnergyFill(), ENERGY_COLOR);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "FE", ENERGY_BAR_X, ENERGY_BAR_Y + ENERGY_BAR_HEIGHT + 4, 0x404040, false);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int fill, int color) {
        if (fill > 0) {
            int fillTop = y + ENERGY_BAR_HEIGHT - fill;
            graphics.fill(x, fillTop, x + ENERGY_BAR_WIDTH, y + ENERGY_BAR_HEIGHT, color);
        }
    }

    private int getCellEnergyFill() {
        if (menu.getEnergyStored() == ItemChargingPowerCell.INFINITE_ENERGY
                || menu.getMaxEnergyStored() == ItemChargingPowerCell.INFINITE_ENERGY) {
            return ENERGY_BAR_HEIGHT;
        }

        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }

    private static String formatEnergy(int stored, int max) {
        if (stored == ItemChargingPowerCell.INFINITE_ENERGY || max == ItemChargingPowerCell.INFINITE_ENERGY) {
            return "∞ FE";
        }
        return NumberFormatter.formatInt(stored) + " / " + NumberFormatter.formatInt(max) + " FE";
    }
}
