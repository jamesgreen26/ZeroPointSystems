package g_mungus.zps.client.ponder;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.client.screens.components.MultiLineCommandSuggestions;
import g_mungus.zps.networking.ExecutorBlocksS2CPacket;
import g_mungus.zps.networking.GetterBlocksS2CPacket;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.lang.ClientFontHelper;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderChapter;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ZPSPonderTagScreen extends AbstractPonderScreen {
    private static final int ITEM_CELL_WIDTH = 28;
    private static final int ITEM_CELL_HEIGHT = 28;
    private static final int ITEM_SPACING = 8;
    private static final int ITEM_ROW_STRIDE = ITEM_CELL_HEIGHT + ITEM_SPACING;
    private static final int MAX_VISIBLE_ROWS = 3;
    private static final int MAX_COLUMNS = 11;

    private final PonderTag tag;
    protected final List<ItemEntry> items = new ArrayList<>();
    @Nullable
    protected Rect2i itemArea;
    protected final List<PonderChapter> chapters = new ArrayList<>();
    @Nullable
    protected Rect2i chapterArea;

    private final List<PonderButton> itemButtons = new ArrayList<>();
    private final List<Integer> itemButtonBaseY = new ArrayList<>();
    private final Map<PonderButton, ResourceLocation> itemButtonKeys = new HashMap<>();
    private int itemScroll;
    private int maxItemScroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private ItemStack hoveredItem = ItemStack.EMPTY;
    @Nullable
    private ResourceLocation hoveredItemKey;

    public ZPSPonderTagScreen(ResourceLocation tag) {
        this(PonderIndex.getTagAccess().getRegisteredTag(tag));
    }

    public ZPSPonderTagScreen(PonderTag tag) {
        this.tag = tag;
    }

    @Override
    protected void init() {
        super.init();

        itemButtons.clear();
        itemButtonBaseY.clear();
        itemButtonKeys.clear();
        itemScroll = 0;
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
        hoveredItem = ItemStack.EMPTY;
        hoveredItemKey = null;

        items.clear();
        PonderIndex.getTagAccess()
                .getItems(tag)
                .stream()
                .map(key -> new ItemEntry(RegisteredObjectsHelper.getItemOrBlock(key), key))
                .filter(entry -> entry.item != null)
                .forEach(items::add);

        if (!tag.getMainItem().isEmpty()) {
            items.removeIf(entry -> entry.item == tag.getMainItem().getItem());
        }

        sortItemsByCreativeSearchOrder();

        int columns = getColumnCount();
        int rowCount = Math.max(1, (int) Math.ceil(items.size() / (double) columns));
        int visibleRows = Math.min(rowCount, MAX_VISIBLE_ROWS);

        int gridWidth = columns * ITEM_CELL_WIDTH + (columns - 1) * ITEM_SPACING;
        int viewportHeight = visibleRows * ITEM_CELL_HEIGHT + (visibleRows - 1) * ITEM_SPACING;
        int contentHeight = rowCount * ITEM_CELL_HEIGHT + (rowCount - 1) * ITEM_SPACING;

        itemArea = new Rect2i(-gridWidth / 2, -viewportHeight / 2, gridWidth, viewportHeight);
        maxItemScroll = Math.max(0, contentHeight - viewportHeight);

        int itemCenterX = width / 2;
        int itemCenterY = getItemsY();
        int top = itemCenterY + itemArea.getY();

        for (int i = 0; i < items.size(); i++) {
            ItemEntry entry = items.get(i);
            int row = i / columns;
            int column = i % columns;
            int rowSize = Math.min(columns, items.size() - row * columns);
            int rowWidth = rowSize * ITEM_CELL_WIDTH + (rowSize - 1) * ITEM_SPACING;
            int x = itemCenterX - rowWidth / 2 + column * ITEM_ROW_STRIDE + 4;
            int y = top + row * ITEM_ROW_STRIDE + 4;

            PonderButton button = new ViewportPonderButton(x, y)
                    .showing(new ItemStack(entry.item));

            if (PonderIndex.getSceneAccess().doScenesExistForId(entry.key)) {
                button.withCallback((mouseX, mouseY) -> {
                    centerScalingOn(mouseX, mouseY);
                    ScreenOpener.transitionTo(PonderUI.of(new ItemStack(entry.item), tag));
                });
            } else {
                button.withBorderColors(
                        entry.key.getNamespace().equals("minecraft") ?
                                PonderUI.MISSING_VANILLA_ENTRY :
                                PonderUI.MISSING_MODDED_ENTRY
                ).animateColors(false);
            }

            itemButtons.add(button);
            itemButtonBaseY.add(y);
            itemButtonKeys.put(button, entry.key);
            addRenderableWidget(button);
        }

        if (!tag.getMainItem().isEmpty()) {
            ResourceLocation registryName = RegisteredObjectsHelper.getKeyOrThrow(tag.getMainItem().getItem());

            PonderButton button = new PonderButton(itemCenterX - gridWidth / 2 - 48, itemCenterY - 10)
                    .showing(tag.getMainItem());

            if (PonderIndex.getSceneAccess().doScenesExistForId(registryName)) {
                button.withCallback((mouseX, mouseY) -> {
                    centerScalingOn(mouseX, mouseY);
                    ScreenOpener.transitionTo(PonderUI.of(tag.getMainItem(), tag));
                });
            } else {
                button.withBorderColors(
                        registryName.getNamespace().equals("minecraft") ?
                                PonderUI.MISSING_VANILLA_ENTRY :
                                PonderUI.MISSING_MODDED_ENTRY
                ).animateColors(false);
            }

            addRenderableWidget(button);
            itemButtonKeys.put(button, registryName);
        }

        updateItemButtonPositions();
    }

    @Override
    protected void initBackTrackIcon(BoxWidget backTrack) {
        backTrack.showing(tag);
    }

    @Override
    public void tick() {
        super.tick();
        PonderUI.ponderTicks++;

        hoveredItem = ItemStack.EMPTY;
        hoveredItemKey = null;
        Window window = minecraft.getWindow();
        int mouseX = (int) (this.minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int mouseY = (int) (this.minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        for (GuiEventListener child : children()) {
            if (child == backTrack) {
                continue;
            }
            if (child instanceof PonderButton button && button.isMouseOver(mouseX, mouseY)) {
                hoveredItem = button.getItem();
                hoveredItemKey = itemButtonKeys.get(button);
            }
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        renderItems(graphics, mouseX, mouseY);
        renderChapters(graphics);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(width / 2f - 120, height * 0.15 - 40, 0);

        poseStack.pushPose();
        int x = 31 + 20 + 8;
        int y = 31;

        String title = tag.getTitle();

        int streakHeight = 35;
        UIRenderHelper.streak(graphics, 0, x - 4, y - 12 + streakHeight / 2, streakHeight, 240);
        new BoxElement()
                .withBackground(PonderUI.BACKGROUND_FLAT)
                .gradientBorder(PonderUI.COLOR_IDLE)
                .at(21, 21, 100)
                .withBounds(30, 30)
                .render(graphics);

        graphics.drawString(font, Ponder.lang().translate(AbstractPonderScreen.PONDERING_TAG).component(), x, y - 6, UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB(), false);
        y += 8;
        poseStack.translate(x, y, 0);
        poseStack.translate(0, 0, 5);
        graphics.drawString(font, title, 0, 0, UIRenderHelper.COLOR_TEXT.getFirst().getRGB(), false);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(23, 23, 10);
        poseStack.scale(1.66f, 1.66f, 1.66f);
        tag.render(graphics, 0, 0);
        poseStack.popPose();
        poseStack.popPose();

        poseStack.pushPose();
        int descriptionWidth = (int) (width * .45);
        x = (width - descriptionWidth) / 2;
        y = getItemsY() - 10 + Math.max(itemArea.getHeight(), 48);

        String description = tag.getDescription();
        int descriptionHeight = font.wordWrapHeight(description, descriptionWidth);

        new BoxElement()
                .withBackground(PonderUI.BACKGROUND_FLAT)
                .gradientBorder(PonderUI.COLOR_IDLE)
                .at(x - 3, y - 3, 90)
                .withBounds(descriptionWidth + 6, descriptionHeight + 6)
                .render(graphics);

        poseStack.translate(0, 0, 100);
        ClientFontHelper.drawSplitString(graphics, poseStack, font, description, x, y, descriptionWidth, UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
        poseStack.popPose();
    }

    protected void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        if (items.isEmpty()) {
            return;
        }

        int x = width / 2;
        int y = getItemsY();

        String relatedTitle = Ponder.lang().translate(AbstractPonderScreen.ASSOCIATED).string();
        int stringWidth = font.width(relatedTitle);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        new BoxElement()
                .withBackground(PonderUI.BACKGROUND_FLAT)
                .gradientBorder(PonderUI.COLOR_IDLE)
                .at((windowWidth - stringWidth) / 2f - 5, itemArea.getY() - 21, 100)
                .withBounds(stringWidth + 10, 10)
                .render(graphics);

        poseStack.translate(0, 0, 200);
        graphics.drawCenteredString(font, relatedTitle, windowWidth / 2, itemArea.getY() - 20, UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
        poseStack.translate(0, 0, -200);

        UIRenderHelper.streak(graphics, 0, 0, 0, itemArea.getHeight() + 10, itemArea.getWidth() / 2 + 75);
        UIRenderHelper.streak(graphics, 180, 0, 0, itemArea.getHeight() + 10, itemArea.getWidth() / 2 + 75);
        poseStack.popPose();

        renderScrollbar(graphics, mouseX, mouseY);
    }

    public int getItemsY() {
        return (int) (0.15 * height + 85);
    }

    protected void renderChapters(GuiGraphics graphics) {
        if (chapters.isEmpty()) {
            return;
        }

        int chapterX = width / 2;
        int chapterY = (int) (height * 0.75);

        graphics.pose().pushPose();
        graphics.pose().translate(chapterX, chapterY, 0);

        UIRenderHelper.streak(graphics, 0, chapterArea.getX() - 10, chapterArea.getY() - 20, 20, 220);
        graphics.drawString(font, "More Topics to Ponder about", chapterArea.getX() - 5, chapterArea.getY() - 25, UIRenderHelper.COLOR_TEXT_ACCENT.getFirst().getRGB(), false);

        graphics.pose().popPose();
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.disableDepthTest();
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        if (!hoveredItem.isEmpty()) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hoveredItem));
            appendScriptCommandTooltips(tooltip);
            graphics.renderTooltip(font, tooltip, hoveredItem.getTooltipImage(), hoveredItem, mouseX, mouseY);
        }

        poseStack.popPose();
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxItemScroll > 0 && isMouseOverItemArea(mouseX, mouseY)) {
            setItemScroll(itemScroll - (int) Math.signum(scrollY) * ITEM_ROW_STRIDE);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxItemScroll > 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarDragOffset = isMouseOverScrollbarThumb(mouseX, mouseY)
                    ? (int) mouseY - getScrollbarThumbTop()
                    : getScrollbarThumbHeight() / 2;
            updateScrollFromScrollbarMouse(mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            updateScrollFromScrollbarMouse(mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected String getBreadcrumbTitle() {
        return tag.getTitle();
    }

    public ItemStack getHoveredTooltipItem() {
        return hoveredItem;
    }

    @Override
    public boolean isEquivalentTo(NavigatableSimiScreen other) {
        if (other instanceof ZPSPonderTagScreen screen) {
            return tag.equals(screen.tag);
        }
        if (other instanceof PonderTagScreen screen) {
            return tag.equals(screen.getTag());
        }
        return super.isEquivalentTo(other);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    public PonderTag getTag() {
        return tag;
    }

    @Override
    public void removed() {
        super.removed();
        hoveredItem = ItemStack.EMPTY;
        hoveredItemKey = null;
    }

    private void appendScriptCommandTooltips(List<Component> tooltip) {
        if (!tag.getId().equals(ZPSPonderTags.HAS_SCRIPT_CAPS) || hoveredItemKey == null) {
            return;
        }

        List<String> executorNames = getSortedNames(ExecutorBlocksS2CPacket.command_names_by_block.getOrDefault(hoveredItemKey, Set.of()));
        List<String> getterNames = getSortedNames(GetterBlocksS2CPacket.getter_names_by_block.getOrDefault(hoveredItemKey, Set.of()));

        if (executorNames.isEmpty() && getterNames.isEmpty()) {
            return;
        }

        tooltip.add(CommonComponents.EMPTY);
        appendCommandGroup(tooltip, "Executors", executorNames, MultiLineCommandSuggestions.EXECUTOR_COLOR);
        appendCommandGroup(tooltip, "Getters", getterNames, MultiLineCommandSuggestions.GETTER_COLOR);
    }

    private static List<String> getSortedNames(Collection<String> names) {
        return names.stream()
                .sorted(String::compareTo)
                .toList();
    }

    private static void appendCommandGroup(List<Component> tooltip, String title, List<String> names, int color) {
        if (names.isEmpty()) {
            return;
        }

        tooltip.add(Component.literal(title).withStyle(ChatFormatting.GRAY));
        names.forEach(name -> tooltip.add(Component.literal("  " + name)
                .withStyle(style -> style.withColor(color))));
    }

    private void sortItemsByCreativeSearchOrder() {
        Map<Item, Integer> searchOrder = new HashMap<>();
        int index = 0;
        for (ItemStack stack : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
            searchOrder.putIfAbsent(stack.getItem(), index);
            index++;
        }

        int missingOrder = searchOrder.size();
        items.sort(Comparator
                .comparingInt((ItemEntry entry) -> searchOrder.getOrDefault(entry.item.asItem(), missingOrder))
                .thenComparing(entry -> entry.key.toString()));
    }

    private int getColumnCount() {
        boolean hasMainItem = !tag.getMainItem().isEmpty();
        int availableWidth = Math.max(ITEM_CELL_WIDTH, width - (hasMainItem ? 112 : 64));
        int screenColumns = Math.max(1, (availableWidth + ITEM_SPACING) / ITEM_ROW_STRIDE);
        return Mth.clamp(Math.min(items.size(), screenColumns), 1, MAX_COLUMNS);
    }

    private boolean isMouseOverItemArea(double mouseX, double mouseY) {
        if (itemArea == null) {
            return false;
        }

        int centerX = width / 2;
        int centerY = getItemsY();
        int left = centerX + itemArea.getX();
        int top = centerY + itemArea.getY();
        return mouseX >= left
                && mouseX < left + itemArea.getWidth()
                && mouseY >= top
                && mouseY < top + itemArea.getHeight();
    }

    private void updateItemButtonPositions() {
        if (itemArea == null) {
            return;
        }

        int top = getItemsY() + itemArea.getY();
        int bottom = top + itemArea.getHeight();

        for (int i = 0; i < itemButtons.size(); i++) {
            PonderButton button = itemButtons.get(i);
            int y = itemButtonBaseY.get(i) - itemScroll;
            boolean visible = y + button.getHeight() > top && y < bottom;

            button.setY(y);
            button.visible = visible;
            button.active = visible;
        }
    }

    private void setItemScroll(int scroll) {
        itemScroll = Mth.clamp(scroll, 0, maxItemScroll);
        updateItemButtonPositions();
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (itemArea == null || maxItemScroll <= 0) {
            return;
        }

        int trackX = getScrollbarTrackX();
        int trackTop = getScrollbarTrackTop();
        int trackHeight = getScrollbarTrackHeight();
        int thumbTop = getScrollbarThumbTop();
        int thumbHeight = getScrollbarThumbHeight();
        boolean hover = draggingScrollbar || isMouseOverScrollbarThumb(mouseX, mouseY);
        int thumbColor = hover ? 0xf0c0c0ff : 0xa0c0c0ff;

        graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0x50202020);
        graphics.fill(trackX - 2, thumbTop, trackX + 4, thumbTop + thumbHeight, thumbColor);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (itemArea == null || maxItemScroll <= 0) {
            return false;
        }

        int trackX = getScrollbarTrackX();
        int trackTop = getScrollbarTrackTop();
        return mouseX >= trackX - 4
                && mouseX < trackX + 6
                && mouseY >= trackTop
                && mouseY < trackTop + getScrollbarTrackHeight();
    }

    private boolean isMouseOverScrollbarThumb(double mouseX, double mouseY) {
        if (!isMouseOverScrollbar(mouseX, mouseY)) {
            return false;
        }

        int thumbTop = getScrollbarThumbTop();
        return mouseY >= thumbTop && mouseY < thumbTop + getScrollbarThumbHeight();
    }

    private void updateScrollFromScrollbarMouse(double mouseY) {
        int trackTop = getScrollbarTrackTop();
        int thumbTravel = getScrollbarTrackHeight() - getScrollbarThumbHeight();
        if (thumbTravel <= 0) {
            setItemScroll(0);
            return;
        }

        int thumbTop = Mth.clamp((int) mouseY - scrollbarDragOffset, trackTop, trackTop + thumbTravel);
        setItemScroll((thumbTop - trackTop) * maxItemScroll / thumbTravel);
    }

    private int getScrollbarTrackX() {
        return width / 2 + itemArea.getX() + itemArea.getWidth() + 14;
    }

    private int getScrollbarTrackTop() {
        return getItemsY() + itemArea.getY();
    }

    private int getScrollbarTrackHeight() {
        return itemArea.getHeight();
    }

    private int getScrollbarThumbHeight() {
        int trackHeight = getScrollbarTrackHeight();
        return Math.max(12, trackHeight * trackHeight / (trackHeight + maxItemScroll));
    }

    private int getScrollbarThumbTop() {
        int thumbTravel = getScrollbarTrackHeight() - getScrollbarThumbHeight();
        return getScrollbarTrackTop() + itemScroll * thumbTravel / maxItemScroll;
    }

    public record ItemEntry(@Nullable ItemLike item, ResourceLocation key) {
    }

    private class ViewportPonderButton extends PonderButton {
        private ViewportPonderButton(int x, int y) {
            super(x, y);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (itemArea == null || !visible) {
                return;
            }

            int left = ZPSPonderTagScreen.this.width / 2 + itemArea.getX();
            int top = getItemsY() + itemArea.getY();
            graphics.enableScissor(left, top, left + itemArea.getWidth(), top + itemArea.getHeight());
            super.renderWidget(graphics, mouseX, mouseY, partialTicks);
            graphics.disableScissor();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return isMouseOverItemArea(mouseX, mouseY) && super.isMouseOver(mouseX, mouseY);
        }
    }
}
