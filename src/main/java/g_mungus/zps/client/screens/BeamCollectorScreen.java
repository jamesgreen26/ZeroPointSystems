package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.menu.BeamCollectorMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The Beam Collector's screen, the size of a chest's and in the dark-panelled style of the Vaporizer's: the pooled
 * inventory (9, 15 or 21 slots in three rows, drawn to match the panel, the smaller two centred) and the energy bar on the
 * right. The title is the only text. There is nothing to set here: the beam is run, and its range set, by
 * redstone.
 */
public class BeamCollectorScreen extends AbstractContainerScreen<BeamCollectorMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/beam_collector.png");
    /** The title is the only text, and it sits on the dark panel, as the Vaporizer's does. */
    private static final int PANEL_TEXT_COLOR = 0xC8D0E0;
    /** The energy well, where the Vaporizer's texture has it. */
    private static final int ENERGY_BAR_X = 152;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_WIDTH = 11;
    private static final int ENERGY_BAR_HEIGHT = 53;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    private static final int SLOT_SPRITE_U = 176;
    private static final int SLOT_SPRITE_V = 0;

    public BeamCollectorScreen(BeamCollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(NumberFormatter.formatInt(menu.getEnergyStored())
                    + " / " + NumberFormatter.formatInt(menu.getMaxEnergyStored()) + " FE"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        int columns = BeamCollectorMenu.columnsFor(menu.getBeamSlots());
        for (int slot = 0; slot < menu.getBeamSlots(); slot++) {
            graphics.blit(TEXTURE,
                    x + BeamCollectorMenu.gridLeft(menu.getBeamSlots()) - 1 + (slot % columns) * 18,
                    y + BeamCollectorMenu.gridTop(menu.getBeamSlots()) - 1 + (slot / columns) * 18,
                    SLOT_SPRITE_U, SLOT_SPRITE_V, 18, 18);
        }

        int fill = energyFill();
        if (fill > 0) {
            int barBottom = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT;
            graphics.fill(x + ENERGY_BAR_X, barBottom - fill, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, barBottom,
                    ENERGY_COLOR);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // Not super: that would also write "Inventory" over the player's slots, and the title in a grey that is
        // lost against the dark panel.
        graphics.drawString(font, title, titleLabelX, titleLabelY, PANEL_TEXT_COLOR, false);
    }

    private int energyFill() {
        int max = menu.getMaxEnergyStored();
        if (max <= 0) {
            return 0;
        }
        return (int) Mth.clamp(((long) menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / max, 0, ENERGY_BAR_HEIGHT);
    }
}
