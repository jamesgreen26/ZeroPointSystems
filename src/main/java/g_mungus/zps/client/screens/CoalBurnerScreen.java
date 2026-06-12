package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.menu.CoalBurnerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class CoalBurnerScreen extends AbstractContainerScreen<CoalBurnerMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/coal_burner.png");
    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_BAR_WIDTH = 10;
    private static final int ENERGY_BAR_HEIGHT = 50;
    private static final int BURN_FLAME_X = 80;
    private static final int BURN_FLAME_Y = 56;
    private static final int BURN_FLAME_WIDTH = 14;
    private static final int BURN_FLAME_HEIGHT = 14;

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
        } else if (isHovering(BURN_FLAME_X, BURN_FLAME_Y, BURN_FLAME_WIDTH, BURN_FLAME_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.literal(formatSeconds(menu.getBurnTime()) + " / " + formatSeconds(menu.getTotalBurnTime())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int burnProgress = getBurnProgress();
        if (burnProgress > 0) {
            graphics.blit(TEXTURE, x + BURN_FLAME_X, y + BURN_FLAME_Y + BURN_FLAME_HEIGHT - burnProgress,
                    176, BURN_FLAME_HEIGHT - burnProgress, BURN_FLAME_WIDTH, burnProgress + 1);
        }

        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X, fillTop, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, 0xFF2380A8);
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
        return Mth.clamp((menu.getBurnTime() * BURN_FLAME_HEIGHT) / totalBurnTime, 0, BURN_FLAME_HEIGHT);
    }

    private int getEnergyFill() {
        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }

    private static String formatSeconds(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return seconds + "s";
    }
}
