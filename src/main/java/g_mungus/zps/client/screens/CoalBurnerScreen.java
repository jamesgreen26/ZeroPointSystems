package g_mungus.zps.client.screens;

import g_mungus.zps.menu.CoalBurnerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class CoalBurnerScreen extends AbstractContainerScreen<CoalBurnerMenu> {
    private static final ResourceLocation FURNACE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/furnace.png");
    private static final int ENERGY_BAR_X = 152;
    private static final int ENERGY_BAR_Y = 17;
    private static final int ENERGY_BAR_WIDTH = 12;
    private static final int ENERGY_BAR_HEIGHT = 52;

    public CoalBurnerScreen(CoalBurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.literal(menu.getEnergyStored() + " / " + menu.getMaxEnergyStored() + " FE"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(FURNACE_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int burnProgress = getBurnProgress();
        if (burnProgress > 0) {
            graphics.blit(FURNACE_TEXTURE, x + 56, y + 36 + 13 - burnProgress, 176, 13 - burnProgress, 14, burnProgress + 1);
        }

        graphics.fill(x + ENERGY_BAR_X, y + ENERGY_BAR_Y, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, 0xFF1B1B1B);
        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X + 1, fillTop, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH - 1, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - 1, 0xFF2380A8);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "FE", ENERGY_BAR_X, ENERGY_BAR_Y + ENERGY_BAR_HEIGHT + 4, 0x404040, false);
    }

    private int getBurnProgress() {
        int totalBurnTime = menu.getTotalBurnTime();
        if (totalBurnTime <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getBurnTime() * 13) / totalBurnTime, 0, 13);
    }

    private int getEnergyFill() {
        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }
}
