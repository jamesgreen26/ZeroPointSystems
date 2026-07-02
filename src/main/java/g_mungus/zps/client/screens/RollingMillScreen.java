package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.recipebook.RollingMillRecipeBookComponent;
import g_mungus.zps.menu.RollingMillMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class RollingMillScreen extends AbstractContainerScreen<RollingMillMenu> implements RecipeUpdateListener {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/rolling_mill.png");
    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_BAR_WIDTH = 10;
    private static final int ENERGY_BAR_HEIGHT = 50;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 34;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_TEXTURE_U = 176;
    private static final int ARROW_TEXTURE_V = 14;
    private static final int ROLLER_X = 67;
    private static final int TOP_ROLLER_Y = -16;
    private static final int BOTTOM_ROLLER_Y = 53;
    private static final int ROLLER_SIZE = 48;
    private static final int ROLLER_TEXTURE_U = 200;
    private static final int ROLLER_TEXTURE_V = 0;
    private static final int ROLLER_FRAME_COUNT = 4;
    private static final int ROLLER_TICKS_PER_FRAME = 2;
    private static final int ROLLER_GRACE_TICKS = 2;
    private static final int PLAYER_INVENTORY_SLOT_TOP = 83;

    private final RollingMillRecipeBookComponent recipeBookComponent = new RollingMillRecipeBookComponent();
    private boolean widthTooNarrow;

    private int rollerFrame;
    private int rollerFrameTimer;
    private int rollerGraceTimer;

    public RollingMillScreen(RollingMillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        this.addRenderableWidget(new ImageButton(this.leftPos + 20, this.height / 2 - 49, 20, 18,
                RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
            this.recipeBookComponent.toggleVisibility();
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            button.setPosition(this.leftPos + 20, this.height / 2 - 49);
        }));
        this.titleLabelX = 8;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.recipeBookComponent.tick();
        tickRollers();
    }

    private void tickRollers() {
        if (isProgressAdvancing()) {
            this.rollerGraceTimer = ROLLER_GRACE_TICKS;
        } else if (this.rollerGraceTimer > 0) {
            this.rollerGraceTimer--;
        } else {
            this.rollerFrame = 0;
            this.rollerFrameTimer = 0;
            return;
        }

        if (++this.rollerFrameTimer >= ROLLER_TICKS_PER_FRAME) {
            this.rollerFrameTimer = 0;
            this.rollerFrame = (this.rollerFrame + 1) % ROLLER_FRAME_COUNT;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.renderGhostRecipe(graphics, this.leftPos, this.topPos, false, partialTick);
        }

        this.renderTooltipUnlessGhostSlot(graphics, mouseX, mouseY);
        this.renderEnergyTooltip(graphics, mouseX, mouseY);
        this.recipeBookComponent.renderTooltip(graphics, this.leftPos, this.topPos, mouseX, mouseY);
    }

    private void renderTooltipUnlessGhostSlot(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.recipeBookComponent.isGhostSlot(this.hoveredSlot)) {
            return;
        }
        this.renderTooltip(graphics, mouseX, mouseY);
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

        renderRollers(graphics, x, y);

        int progressWidth = getProgressWidth();
        if (progressWidth > 0) {
            graphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y,
                    ARROW_TEXTURE_U, ARROW_TEXTURE_V, progressWidth, ARROW_HEIGHT);
        }

        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X, fillTop, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, ENERGY_COLOR);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "FE", ENERGY_BAR_X, ENERGY_BAR_Y + ENERGY_BAR_HEIGHT + 4, 0x404040, false);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack itemStack, Slot slot, @Nullable String countString) {
        if (this.recipeBookComponent.isGhostSlot(slot)) {
            return;
        }
        super.renderSlotContents(graphics, itemStack, slot, countString);
    }

    private void renderRollers(GuiGraphics graphics, int x, int y) {
        int frame = getRollerFrame();

        renderRoller(graphics, x + ROLLER_X, y + TOP_ROLLER_Y, reverseRollerFrame(frame));

        graphics.enableScissor(x + ROLLER_X, y + BOTTOM_ROLLER_Y,
                x + ROLLER_X + ROLLER_SIZE, y + PLAYER_INVENTORY_SLOT_TOP);
        renderRoller(graphics, x + ROLLER_X, y + BOTTOM_ROLLER_Y, frame);
        graphics.disableScissor();
    }

    private void renderRoller(GuiGraphics graphics, int x, int y, int frame) {
        graphics.blit(TEXTURE, x, y,
                ROLLER_TEXTURE_U, ROLLER_TEXTURE_V + frame * ROLLER_SIZE, ROLLER_SIZE, ROLLER_SIZE);
    }

    private boolean isProgressAdvancing() {
        return menu.getProgress() > 0 && menu.getMaxProgress() > 0 && menu.getEnergyStored() > 0;
    }

    private int getRollerFrame() {
        return this.rollerFrame;
    }

    private int reverseRollerFrame(int frame) {
        return (ROLLER_FRAME_COUNT - frame) % ROLLER_FRAME_COUNT;
    }

    private int getProgressWidth() {
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getProgress() * ARROW_WIDTH) / maxProgress, 0, ARROW_WIDTH);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return this.widthTooNarrow && this.recipeBookComponent.isVisible()
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        this.recipeBookComponent.slotClicked(slot);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean outside = mouseX < left
                || mouseY < top
                || mouseX >= left + this.imageWidth
                || mouseY >= top + this.imageHeight;
        return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos,
                this.imageWidth, this.imageHeight, button) && outside;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.recipeBookComponent.charTyped(codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void recipesUpdated() {
        this.recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent() {
        return this.recipeBookComponent;
    }
}
