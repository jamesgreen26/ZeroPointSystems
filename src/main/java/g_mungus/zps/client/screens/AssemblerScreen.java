package g_mungus.zps.client.screens;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.item.ModComponents;
import g_mungus.zps.menu.AssemblerMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
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

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 280;
        this.imageHeight = 166;
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
        // Explicit texture dimensions are required so the 512x256 file is not treated as 256x256 (which stretches).
        graphics.blit(TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);

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
}
