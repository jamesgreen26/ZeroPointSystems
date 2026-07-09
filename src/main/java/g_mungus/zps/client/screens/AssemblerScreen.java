package g_mungus.zps.client.screens;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.item.ModComponents;
import g_mungus.zps.menu.AssemblerMenu;
import g_mungus.zps.mixin.RecipeBookComponentAccessor;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> implements RecipeUpdateListener {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/assembler.png");
    // The texture file is 512x256 (vanilla villager-menu size); the drawn GUI area is 280x166.
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int ENERGY_BAR_X = 257;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_WIDTH = 10;
    private static final int ENERGY_BAR_HEIGHT = 54;
    private static final int ENERGY_COLOR = 0xFF2380A8;
    /** Center of the energy well (256..266) for centering the FE label. */
    private static final int ENERGY_CENTER_X = 261;

    // Progress arrow: empty arrow is baked into the GUI at (ARROW_X, ARROW_Y); the filled sprite lives
    // in the texture's free area at (ARROW_U, ARROW_V) and is blitted left-to-right by progress.
    private static final int ARROW_X = 186;
    private static final int ARROW_Y = 34;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_U = 186;
    private static final int ARROW_V = 180;

    // Only the left blue panel of the texture (up to the black seam before the grey panels) is drawn when the
    // right side is collapsed.
    private static final int LEFT_PANEL_WIDTH = 104;

    // Book/trash buttons, centered (relative to leftPos/topPos) in the empty blue space below the pattern grid.
    // Icons are 16x16, drawn with no background or outline. Left = book (toggle panel), right = trash (clear).
    private static final int PANEL_BUTTON_SIZE = 16;
    private static final int PANEL_BUTTON_Y = 135;
    private static final int BOOK_BUTTON_X = 32;
    private static final int TRASH_BUTTON_X = 56;
    // The green book that vanilla uses for its recipe-book toggle button (same art as the knowledge book item).
    private static final ItemStack BOOK_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);
    // Barrier block texture for the clear-pattern (trash) button.
    private static final ItemStack CLEAR_ICON = new ItemStack(Items.BARRIER);
    private static final Component TOGGLE_PANEL_TOOLTIP = Component.translatable("gui.zps.assembler.toggle_panel");
    private static final Component CLEAR_PATTERN_TOOLTIP = Component.translatable("gui.zps.assembler.clear_pattern");

    // The embedded vanilla recipe book fills the space where the grey panels were: its left edge sits just past
    // the blue panel's seam. The book positions everything from its stored `width`, so we feed init() a width
    // that lands the 147px book at leftPos + RECIPE_BOOK_X (see recipeBookVirtualWidth). Category tabs, which
    // vanilla draws on the book's left, are moved to the right edge each frame (repositionRecipeBookTabs).
    private static final int RECIPE_BOOK_X = 104;
    private static final int RECIPE_BOOK_X_OFFSET = 86; // RecipeBookComponent's fixed xOffset when not width-constrained.
    private static final int TAB_RIGHT_OVERLAP = 5;     // Pulls the tabs left so their flat edge tucks under the book.

    // Right-facing tab backgrounds: the vanilla sprite mirrored in shape, re-shaded for the GUI's top-left
    // light so the highlight stays on top and the protruding right/bottom edges read as shadow.
    private static final ResourceLocation TAB_TEXTURE = ZPSMod.resource("textures/gui/recipe_tab.png");
    private static final ResourceLocation TAB_SELECTED_TEXTURE = ZPSMod.resource("textures/gui/recipe_tab_selected.png");
    private static final int TAB_WIDTH = 35;
    private static final int TAB_HEIGHT = 27;

    private final RecipeBookComponent recipeBook = new RecipeBookComponent();

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 280;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // Feed a virtual screen width so the book lands to the right of the blue panel, and the real height so
        // its top aligns with topPos. widthTooNarrow is forced false so the book keeps its side-by-side layout.
        this.recipeBook.init(recipeBookVirtualWidth(), this.height, this.minecraft, false, this.menu);
        this.addWidget(this.recipeBook);
        // init() seeds visibility from saved book data; force it to follow our collapse state instead.
        syncRecipeBookVisibility();
    }

    /** The width to hand {@link RecipeBookComponent#init} so its 147px book renders at {@code leftPos + RECIPE_BOOK_X}. */
    private int recipeBookVirtualWidth() {
        // book x = (width - 147) / 2 - xOffset  ==>  width = 2 * (leftPos + RECIPE_BOOK_X + xOffset) + 147
        return 2 * (this.leftPos + RECIPE_BOOK_X + RECIPE_BOOK_X_OFFSET) + RecipeBookComponent.IMAGE_WIDTH;
    }

    /** Drives the recipe book's visibility from the panel-collapsed state (they are always shown together). */
    private void syncRecipeBookVisibility() {
        if (this.recipeBook.isVisible() != this.menu.isRightPanelCollapsed()) {
            this.recipeBook.toggleVisibility();
        }
    }

    /**
     * Moves the category tab buttons from the book's left edge (vanilla default) to its right edge, and swaps
     * each vanilla button for a {@link RightSideTabButton} that draws a right-facing background. Vanilla rebuilds
     * the tab list and recomputes positions only on init/visibility changes, so we re-apply every frame; the
     * buttons carry their own hitbox, so click detection follows.
     */
    private void repositionRecipeBookTabs() {
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) this.recipeBook;
        List<RecipeBookTabButton> tabs = accessor.getTabButtons();
        RecipeBookTabButton selected = accessor.getSelectedTab();
        int tabX = this.leftPos + RECIPE_BOOK_X + RecipeBookComponent.IMAGE_WIDTH - TAB_RIGHT_OVERLAP;

        for (int i = 0; i < tabs.size(); i++) {
            RecipeBookTabButton tab = tabs.get(i);
            if (!(tab instanceof RightSideTabButton)) {
                // Replace the vanilla button in place, carrying over its category, selection and layout, and
                // keep the component's selectedTab reference pointing at the live instance.
                RightSideTabButton replacement = new RightSideTabButton(tab.getCategory());
                replacement.visible = tab.visible;
                replacement.setStateTriggered(tab.isStateTriggered());
                replacement.setPosition(tab.getX(), tab.getY());
                tabs.set(i, replacement);
                if (selected == tab) {
                    accessor.setSelectedTab(replacement);
                }
                tab = replacement;
            }
            if (tab.visible) {
                tab.setX(tabX);
            }
        }
    }

    @Override
    public void recipesUpdated() {
        this.recipeBook.recipesUpdated();
    }

    @Override
    public @NotNull RecipeBookComponent getRecipeBookComponent() {
        return this.recipeBook;
    }

    private static final int LABEL_COLOR = 0x404040;
    /** Opacity for ghost/pattern preview items. */
    private static final float GHOST_ALPHA = 0.6F;

    /** Ghost cells already stamped during the current click-drag, so each cell is set only once per drag. */
    private final Set<Integer> draggedGhostSlots = new HashSet<>();

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

    /** True if the cursor is over the given panel button (coordinates relative to the GUI origin). */
    private boolean overPanelButton(double mouseX, double mouseY, int buttonX) {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        return x >= buttonX && x < buttonX + PANEL_BUTTON_SIZE
                && y >= PANEL_BUTTON_Y && y < PANEL_BUTTON_Y + PANEL_BUTTON_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overPanelButton(mouseX, mouseY, BOOK_BUTTON_X)) {
                this.menu.toggleRightPanel();
                syncRecipeBookVisibility();
                playButtonClick();
                return true;
            }
            if (overPanelButton(mouseX, mouseY, TRASH_BUTTON_X)) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, AssemblerMenu.BUTTON_CLEAR_PATTERN);
                }
                playButtonClick();
                return true;
            }
        }
        if (this.recipeBook.isVisible() && this.recipeBook.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.recipeBook);
            return true;
        }
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

    private void playButtonClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.recipeBook.isVisible() && this.recipeBook.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.recipeBook.isVisible() && this.recipeBook.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean outsideGui = super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
        if (this.recipeBook.isVisible()) {
            // Keep clicks on the book (which spills past the GUI's right edge) from closing the screen.
            return outsideGui && this.recipeBook.hasClickedOutside(
                    mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseButton);
        }
        return outsideGui;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.recipeBook.isVisible()) {
            repositionRecipeBookTabs();
            this.recipeBook.render(graphics, mouseX, mouseY, partialTick);
        }
        renderPanelButtons(graphics);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (!this.menu.isRightPanelCollapsed()) {
            renderEnergyTooltip(graphics, mouseX, mouseY);
        }
        renderPanelButtonTooltips(graphics, mouseX, mouseY);
        if (this.recipeBook.isVisible()) {
            this.recipeBook.renderTooltip(graphics, this.leftPos, this.topPos, mouseX, mouseY);
        }
    }

    /** Draws the book (toggle) and trash (clear) icons — icon only, no button background or outline. */
    private void renderPanelButtons(GuiGraphics graphics) {
        graphics.renderItem(BOOK_ICON, this.leftPos + BOOK_BUTTON_X, this.topPos + PANEL_BUTTON_Y);
        graphics.renderItem(CLEAR_ICON, this.leftPos + TRASH_BUTTON_X, this.topPos + PANEL_BUTTON_Y);
    }

    private void renderPanelButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (overPanelButton(mouseX, mouseY, BOOK_BUTTON_X)) {
            graphics.renderTooltip(this.font, TOGGLE_PANEL_TOOLTIP, mouseX, mouseY);
        } else if (overPanelButton(mouseX, mouseY, TRASH_BUTTON_X)) {
            graphics.renderTooltip(this.font, CLEAR_PATTERN_TOOLTIP, mouseX, mouseY);
        }
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
        // Explicit texture dimensions are required so the 512x256 file is not treated as 256x256 (which stretches).
        // When collapsed, only the left blue panel is drawn; the right grey panels (and everything on them) are hidden.
        boolean collapsed = this.menu.isRightPanelCollapsed();
        int drawWidth = collapsed ? LEFT_PANEL_WIDTH : this.imageWidth;
        graphics.blit(TEXTURE, x, y, 0.0F, 0.0F, drawWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (collapsed) {
            return;
        }

        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X, fillTop,
                    x + ENERGY_BAR_X + ENERGY_BAR_WIDTH, y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, ENERGY_COLOR);
        }

        int progressWidth = getProgressWidth();
        if (progressWidth > 0) {
            graphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y, (float) ARROW_U, (float) ARROW_V,
                    progressWidth, ARROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    private int getProgressWidth() {
        int max = menu.getMaxProgress();
        if (max <= 0) {
            return 0;
        }
        return Mth.clamp(menu.getProgress() * ARROW_WIDTH / max, 0, ARROW_WIDTH);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // Section labels replace the machine title. No inventory label.
        graphics.drawString(this.font, Component.translatable("gui.zps.assembler.crafting"),
                AssemblerMenu.GRID_LEFT, 20, LABEL_COLOR, false);
        // The input/FE labels sit on the right grey panels, which are hidden when collapsed.
        if (this.menu.isRightPanelCollapsed()) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.zps.assembler.input"),
                AssemblerMenu.INPUT_LEFT, 6, LABEL_COLOR, false);
        graphics.drawString(this.font, "FE", ENERGY_CENTER_X + 1 - this.font.width("FE") / 2, 6, LABEL_COLOR, false);
    }

    @Override
    protected void renderSlotContents(@NotNull GuiGraphics graphics, @NotNull ItemStack itemStack, @NotNull Slot slot,
                                      @Nullable String countString) {
        // Ghost/pattern cells: render the item as a translucent preview, not real contents. Tag cells cycle
        // their preview through the tag's items (JEI-style).
        if (slot.index < AssemblerBlockEntity.PATTERN_SLOTS && !itemStack.isEmpty()) {
            renderGhostItem(graphics, displayedGhostStack(itemStack), slot.x, slot.y);
            return;
        }
        super.renderSlotContents(graphics, itemStack, slot, countString);
    }

    /** For a tag ghost cell, the item currently shown (cycling once per second); otherwise the stack itself. */
    private ItemStack displayedGhostStack(ItemStack ghost) {
        List<ResourceLocation> tags = ghost.get(ModComponents.GHOST_INGREDIENT_TAGS.get());
        if (tags == null || tags.isEmpty()) {
            return ghost;
        }
        List<ItemStack> items = resolveTagItems(tags);
        if (items.isEmpty()) {
            return ghost;
        }
        int index = (int) ((Util.getMillis() / 1000L) % items.size());
        return items.get(index);
    }

    private static List<ItemStack> resolveTagItems(List<ResourceLocation> tagIds) {
        List<ItemStack> items = new ArrayList<>();
        for (ResourceLocation id : tagIds) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, id);
            BuiltInRegistries.ITEM.getTag(key).ifPresent(named ->
                    named.forEach(holder -> items.add(new ItemStack(holder))));
        }
        return items;
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<ResourceLocation> tags = stack.get(ModComponents.GHOST_INGREDIENT_TAGS.get());
        if (tags != null && !tags.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, displayedGhostStack(stack)));
            lines.add(Component.translatable("gui.zps.assembler.tag_ingredient").withStyle(ChatFormatting.YELLOW));
            for (ResourceLocation id : tags) {
                lines.add(Component.literal("#" + id).withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        return super.getTooltipFromContainerItem(stack);
    }

    /**
     * Renders a GUI item at {@link #GHOST_ALPHA} opacity. Unlike a plain {@code renderItem} + shader-color
     * tint (which only fades flat item models), this also fades block items by routing their opaque cutout
     * render sheet through the translucent block sheet so the alpha actually blends.
     */
    private void renderGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        ItemRenderer itemRenderer = this.minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, this.minecraft.level, this.minecraft.player, 0);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 8.0, y + 8.0, 150.0);
        pose.scale(16.0F, -16.0F, 16.0F);

        boolean flatLight = !model.usesBlockLight();
        if (flatLight) {
            Lighting.setupForFlatItems();
        }

        MultiBufferSource.BufferSource base = graphics.bufferSource();
        MultiBufferSource translucent = renderType -> base.getBuffer(toTranslucentSheet(renderType));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
        itemRenderer.render(stack, ItemDisplayContext.GUI, false, pose, translucent,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (flatLight) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    /** Swaps the opaque block render sheets for the translucent one so block-item alpha blends. */
    private static RenderType toTranslucentSheet(RenderType renderType) {
        if (renderType == Sheets.solidBlockSheet() || renderType == Sheets.cutoutBlockSheet()) {
            return Sheets.translucentCullBlockSheet();
        }
        return renderType;
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

    /**
     * A recipe-book category tab that draws a purpose-made right-facing background (see {@link #TAB_TEXTURE})
     * instead of the vanilla left-facing sprite, so the shading matches the GUI's light source. Reimplements
     * {@link #renderWidget} because vanilla draws its sprite and icon together; the selected-state "pop" is
     * flipped to push outward to the right, and icons are nudged into the protruding (visible) half of the tab.
     */
    private static final class RightSideTabButton extends RecipeBookTabButton {
        private static final int SELECTED_POP = 2;

        private RightSideTabButton(RecipeBookCategories category) {
            super(category);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ResourceLocation texture = this.isStateTriggered ? TAB_SELECTED_TEXTURE : TAB_TEXTURE;
            int x = this.getX() + (this.isStateTriggered ? SELECTED_POP : 0);
            RenderSystem.disableDepthTest();
            graphics.blit(texture, x, this.getY(), 0, 0, TAB_WIDTH, TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT);
            RenderSystem.enableDepthTest();
            renderIcon(graphics, x);
        }

        /** Draws the category icon(s) centred in the tab's protruding (right) half. */
        private void renderIcon(GuiGraphics graphics, int x) {
            List<ItemStack> icons = this.getCategory().getIconItems();
            int y = this.getY() + 5;
            if (icons.size() == 1) {
                graphics.renderFakeItem(icons.get(0), x + 11, y);
            } else if (icons.size() == 2) {
                graphics.renderFakeItem(icons.get(0), x + 5, y);
                graphics.renderFakeItem(icons.get(1), x + 16, y);
            }
        }
    }
}
