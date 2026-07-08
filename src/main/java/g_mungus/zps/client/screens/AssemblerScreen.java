package g_mungus.zps.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.menu.AssemblerMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/assembler.png");

    private static final int ENERGY_BAR_X = 152;
    private static final int ENERGY_BAR_Y = 35;
    private static final int ENERGY_BAR_WIDTH = 11;
    private static final int ENERGY_BAR_HEIGHT = 72;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Match the vanilla double-chest drawn area. The texture file itself is 256x256 (blit's
        // assumed texture size), with the GUI drawn into its top-left corner.
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    private static final int LABEL_COLOR = 0x404040;

    /** Ghost cells already stamped during the current click-drag, so each cell is set only once per drag. */
    private final Set<Integer> draggedGhostSlots = new HashSet<>();

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 129;
    }

    /** Menu slot id of the ghost grid cell under the cursor, or -1 if the cursor is not over the grid. */
    private int ghostSlotAt(double mouseX, double mouseY) {
        int gx = (int) mouseX - this.leftPos - AssemblerMenu.GRID_LEFT;
        int gy = (int) mouseY - this.topPos - AssemblerMenu.GRID_TOP;
        if (gx < 0 || gy < 0) {
            return -1;
        }
        int col = gx / 18;
        int row = gy / 18;
        if (col >= AssemblerBlockEntity.GRID_WIDTH || row >= AssemblerBlockEntity.GRID_HEIGHT) {
            return -1;
        }
        // Ghost slots are added first (ids 0..PATTERN_SLOTS-1) in this exact row-major order.
        return col + row * AssemblerBlockEntity.GRID_WIDTH;
    }

    /** Stamps the ghost cell via the normal click path (AssemblerMenu#clicked handles it server-side). */
    private void stampGhost(int slotId, int button) {
        this.slotClicked(this.menu.slots.get(slotId), slotId, button, ClickType.PICKUP);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            int id = ghostSlotAt(mouseX, mouseY);
            if (id >= 0) {
                draggedGhostSlots.clear();
                draggedGhostSlots.add(id);
                stampGhost(id, button);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 || button == 1) {
            int id = ghostSlotAt(mouseX, mouseY);
            if (id >= 0) {
                if (draggedGhostSlots.add(id)) {
                    stampGhost(id, button);
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggedGhostSlots.clear();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderEnergyTooltip(graphics, mouseX, mouseY);
    }

    private void renderEnergyTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.literal(formatEnergy(menu.getEnergyStored(), menu.getMaxEnergyStored())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X, fillTop,
                    x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, ENERGY_COLOR);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // Section labels replace the machine title; keep the player inventory label.
        graphics.drawString(this.font, Component.translatable("gui.zps.assembler.input"),
                AssemblerMenu.INPUT_LEFT, 6, LABEL_COLOR, false);
        graphics.drawString(this.font, Component.translatable("gui.zps.assembler.crafting"),
                AssemblerMenu.GRID_LEFT, 6, LABEL_COLOR, false);
        graphics.drawString(this.font, Component.translatable("gui.zps.assembler.output"),
                AssemblerMenu.OUTPUT_LEFT, AssemblerMenu.OUTPUT_TOP - 11, LABEL_COLOR, false);
        graphics.drawString(this.font, "FE", ENERGY_BAR_X, ENERGY_BAR_Y - 10, LABEL_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    protected void renderSlotContents(@NotNull GuiGraphics graphics, @NotNull ItemStack itemStack, @NotNull Slot slot,
                                      @Nullable String countString) {
        // Ghost/pattern cells: render the item at 50% opacity so it reads as a preview, not real contents.
        if (slot.index < AssemblerBlockEntity.PATTERN_SLOTS && !itemStack.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.6F);
            graphics.renderItem(itemStack, slot.x, slot.y);
            graphics.flush();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            return;
        }
        super.renderSlotContents(graphics, itemStack, slot, countString);
    }

    private int getEnergyFill() {
        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }

    private static String formatEnergy(int stored, int max) {
        return NumberFormatter.formatInt(stored) + " / " + NumberFormatter.formatInt(max) + " FE";
    }
}
