package g_mungus.zps.client.screens;

import g_mungus.zps.manual.ManualDocumentLoader;
import g_mungus.zps.manual.ManualSection;
import g_mungus.zps.manual.markdown.ManualDocument;
import g_mungus.zps.manual.render.ManualContentLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public class ManualScreen extends Screen {
    private static final Component TITLE = Component.literal("Script Commands");
    private static final int TOOLTIP_BASE = 0x4C99C9;
    private static final int TOOLTIP_HIGHLIGHT = 0x79F1A3;
    private static final int SCROLLBAR_THUMB = 0xFF4C99C9;
    private static final int SCROLLBAR_THUMB_ACTIVE = 0xFF79F1A3;
    private static final int FRAME_COLOR = 0xE0000000;
    private static final int PANEL_COLOR = 0xE6141414;
    private static final int PANEL_BORDER = 0xFF343434;
    private static final int TAB_SELECTED = 0xFF1C1C1C;
    private static final int TAB_HOVER = 0xEE242424;
    private static final int TAB_NORMAL = 0xD2191919;
    private static final int MUTED_TEXT = 0xA6B8C5;
    private static final int TAB_SCROLLBAR_TRACK = 0x55606060;
    private static final int CODE_BLOCK_BACKGROUND = TAB_NORMAL;
    private static final int CARD_BACKGROUND = 0x99202020;
    private static final int TABLE_HEADER_BACKGROUND = 0xAA242424;
    private static final int TABLE_ROW_BACKGROUND = 0x88303030;
    private static final int SCROLLBAR_TRACK = 0x55606060;
    private static final int BODY_TEXT = 0xFFFFFF;
    private static final int TAB_WIDTH = 75;
    private static final int TAB_AREA_WIDTH = 95;
    private static final int SCROLLBAR_WIDTH = 8;

    private final List<ManualSection> sections;
    private int selectedSectionIndex;
    private final Screen previousScreen;

    private final List<TabButton> tabButtons = new ArrayList<>();
    private Button closeButton;

    private ManualDocument currentDocument = new ManualDocument(List.of());
    private ManualContentLayout cachedLayout = ManualContentLayout.create(Minecraft.getInstance(), Minecraft.getInstance().font, currentDocument, 100);
    private int cachedContentWidth = -1;
    private int scrollAmount = 0;
    private double renderedScrollAmount = 0;
    private int maxScroll = 0;
    private int tabScroll = 0;
    private int maxTabScroll = 0;
    private boolean draggingScrollbar = false;
    private int scrollbarDragOffset = 0;

    public ManualScreen(final List<ManualSection> sections, final int selectedSectionIndex) {
        super(GameNarrator.NO_TITLE);
        this.sections = List.copyOf(sections);
        this.selectedSectionIndex = Math.max(0, Math.min(selectedSectionIndex, sections.size() - 1));
        this.previousScreen = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {
        this.rebuildWidgets();
        this.loadSelectedDocument();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.tabButtons.clear();

        final int frameLeft = this.frameLeft();
        final int frameTop = this.frameTop();
        final int tabAreaTop = frameTop + 34;
        final int tabAreaHeight = this.tabAreaHeight();
        final int totalTabHeight = this.sections.size() * 26;
        this.maxTabScroll = Math.max(0, totalTabHeight - tabAreaHeight);
        this.tabScroll = Math.max(0, Math.min(this.tabScroll, this.maxTabScroll));

        for (int index = 0; index < this.sections.size(); index++) {
            final int y = tabAreaTop + index * 26 - this.tabScroll;
            final TabButton button = new TabButton(frameLeft + 4, y, TAB_WIDTH, 22, this.sections.get(index), index);
            this.tabButtons.add(button);
            this.addRenderableWidget(button);
        }

        this.closeButton = this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
            .bounds(this.frameLeft() + this.frameWidth() - 24, this.frameTop() + 6, 18, 18)
            .build());
    }

    private void loadSelectedDocument() {
        if (this.minecraft == null || this.sections.isEmpty()) {
            return;
        }
        this.currentDocument = ManualDocumentLoader.load(this.minecraft, this.sections.get(this.selectedSectionIndex));
        this.scrollAmount = 0;
        this.renderedScrollAmount = 0;
        this.cachedContentWidth = -1;
        this.refreshLayout();
    }

    private void refreshLayout() {
        if (this.minecraft == null) {
            return;
        }
        final int contentWidth = this.contentWidth();
        if (contentWidth == this.cachedContentWidth) {
            return;
        }
        this.cachedLayout = ManualContentLayout.create(this.minecraft, this.font, this.currentDocument, contentWidth);
        this.cachedContentWidth = contentWidth;
        this.maxScroll = Math.max(0, this.cachedLayout.totalHeight() - this.contentHeight());
        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, this.maxScroll));
        this.renderedScrollAmount = Mth.clamp(this.renderedScrollAmount, 0, this.maxScroll);
    }

    private int frameLeft() {
        return Math.max(16, this.width / 2 - 210);
    }

    private int frameTop() {
        return Math.max(12, (this.height - this.frameHeight()) / 2);
    }

    private int frameWidth() {
        return Math.min(this.width - 32, 420);
    }

    private int frameHeight() {
        return Math.min(this.height - 24, 320);
    }

    private int tabAreaHeight() {
        return this.frameHeight() - 50;
    }

    private int contentLeft() {
        return this.frameLeft() + TAB_AREA_WIDTH;
    }

    private int contentTop() {
        return this.frameTop() + 34;
    }

    private int contentWidth() {
        return this.frameWidth() - (TAB_AREA_WIDTH + 22);
    }

    private int contentHeight() {
        return this.frameHeight() - 46;
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        final int previousMaxScroll = Math.max(1, this.maxScroll);
        final float ratio = (float) this.scrollAmount / previousMaxScroll;
        super.resize(minecraft, width, height);
        this.scrollAmount = Math.round(ratio * this.maxScroll);
        this.renderedScrollAmount = this.scrollAmount;
        this.rebuildWidgets();
        this.cachedContentWidth = -1;
        this.refreshLayout();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseOverTabs(mouseX, mouseY)) {
            this.tabScroll = clampScroll(this.tabScroll - (int) Math.signum(scrollY) * 18, this.maxTabScroll);
            this.rebuildWidgets();
            return true;
        }
        if (this.isMouseOverContent(mouseX, mouseY)) {
            this.scrollAmount = clampScroll(this.scrollAmount - (int) Math.signum(scrollY) * 18, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 || keyCode == 267) {
            this.scrollAmount = clampScroll(this.scrollAmount + 16, this.maxScroll);
            return true;
        }
        if (keyCode == 265 || keyCode == 266) {
            this.scrollAmount = clampScroll(this.scrollAmount - 16, this.maxScroll);
            return true;
        }
        if (keyCode == 268) {
            this.scrollAmount = 0;
            return true;
        }
        if (keyCode == 269) {
            this.scrollAmount = this.maxScroll;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOverScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            final ScrollbarMetrics metrics = this.getScrollbarMetrics();
            this.scrollbarDragOffset = Mth.clamp((int) mouseY - metrics.handleTop(), 0, metrics.handleHeight());
            if (mouseY < metrics.handleTop() || mouseY > metrics.handleBottom()) {
                this.scrollbarDragOffset = metrics.handleHeight() / 2;
                this.updateScrollFromScrollbar(mouseY);
            }
            return true;
        }
        if (button == 0 && this.isMouseOverContent(mouseX, mouseY)) {
            final Style style = this.getStyleAt(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                if (style.getClickEvent().getValue().endsWith(".md")) {
                    this.openDocument(style.getClickEvent().getValue());
                    return true;
                }
                return this.handleComponentClicked(style);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingScrollbar) {
            this.updateScrollFromScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.refreshLayout();
        this.updateSmoothScroll();

        final int frameLeft = this.frameLeft();
        final int frameTop = this.frameTop();
        final int frameRight = frameLeft + this.frameWidth();
        final int frameBottom = frameTop + this.frameHeight();
        graphics.fill(frameLeft, frameTop, frameRight, frameBottom, FRAME_COLOR);
        graphics.fill(frameLeft + 1, frameTop + 1, frameRight - 1, frameBottom - 1, PANEL_COLOR);
        graphics.hLine(frameLeft + 1, frameRight - 2, frameTop + 28, PANEL_BORDER);
        graphics.vLine(this.contentLeft() - 12, frameTop + 6, frameBottom - 34, PANEL_BORDER);
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, frameTop + 10, TOOLTIP_HIGHLIGHT);

        this.renderContent(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.isMouseOverContent(mouseX, mouseY)) {
            final Style style = this.getStyleAt(mouseX, mouseY);
            if (style != null) {
                graphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
            }
        }
    }

    private void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final int left = this.contentLeft();
        final int top = this.contentTop();
        final int right = left + this.contentWidth();
        final int bottom = top + this.contentHeight();
        graphics.enableScissor(left, top, right, bottom);
        final int baseY = top - this.visibleScrollAmount();

        for (ManualContentLayout.Entry entry : this.cachedLayout.entries()) {
            final int entryTop = baseY + entry.y();
            final int entryBottom = entryTop + entry.height(this.font);
            if (entryBottom < top || entryTop > bottom) {
                continue;
            }

            if (entry instanceof ManualContentLayout.TextEntry textEntry) {
                if (textEntry.quoted()) {
                    graphics.fill(left, entryTop, left + 3, entryBottom, TOOLTIP_BASE);
                }
                renderTextLines(graphics, left + textEntry.xOffset(), entryTop, textEntry.lines(), textEntry.scale());
                continue;
            }
            if (entry instanceof ManualContentLayout.ListEntry listEntry) {
                graphics.drawString(this.font, listEntry.marker(), left + listEntry.indent(), entryTop, TOOLTIP_HIGHLIGHT);
                renderTextLines(graphics, left + listEntry.indent() + 12, entryTop, listEntry.lines(), 1);
                continue;
            }
            if (entry instanceof ManualContentLayout.RuleEntry) {
                graphics.fill(left, entryTop + 2, right - 10, entryTop + 3, TOOLTIP_BASE);
                continue;
            }
            if (entry instanceof ManualContentLayout.CodeEntry codeEntry) {
                graphics.fill(left, entryTop, right - 10, entryTop + codeEntry.height(this.font), CODE_BLOCK_BACKGROUND);
                graphics.fill(left, entryTop, left + 2, entryTop + codeEntry.height(this.font), TOOLTIP_HIGHLIGHT);
                renderCodeBlock(graphics, left + 6, entryTop + 6, codeEntry);
                continue;
            }
            if (entry instanceof ManualContentLayout.ImageEntry imageEntry) {
                renderImageEntry(graphics, left, entryTop, right - left - 10, imageEntry);
                continue;
            }
            if (entry instanceof ManualContentLayout.ChartEntry chartEntry) {
                renderChart(graphics, left, entryTop, right - left - 10, chartEntry);
                continue;
            }
            if (entry instanceof ManualContentLayout.TableEntry tableEntry) {
                renderTable(graphics, left, entryTop, tableEntry);
            }
        }

        graphics.disableScissor();
        renderScrollBar(graphics, right + 4, top, bottom);
    }

    private void renderChart(final GuiGraphics graphics, final int x, final int y, final int width, final ManualContentLayout.ChartEntry chartEntry) {
        final int height = chartEntry.height(this.font);
        graphics.fill(x, y, x + width, y + height, CARD_BACKGROUND);
        graphics.drawString(this.font, chartEntry.chart().title(), x + 6, y + 6, TOOLTIP_HIGHLIGHT);
        final double max = Math.max(1.0D, chartEntry.maxValue());
        final int chartLeft = x + 74;
        final int chartWidth = Math.max(20, width - 90);
        int barY = y + 20;
        int previousCenterX = -1;
        int previousCenterY = -1;
        for (ManualDocument.ChartValue value : chartEntry.chart().values()) {
            graphics.drawString(this.font, value.label(), x + 6, barY + 2, TOOLTIP_BASE);
            final int valueWidth = (int) (chartWidth * (value.value() / max));
            if (chartEntry.chart().type() == ManualDocument.ChartType.LINE) {
                final int centerX = chartLeft + valueWidth;
                final int centerY = barY + 6;
                if (previousCenterX >= 0) {
                    drawLine(graphics, previousCenterX, previousCenterY, centerX, centerY, TOOLTIP_BASE);
                }
                graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, TOOLTIP_HIGHLIGHT);
                previousCenterX = centerX;
                previousCenterY = centerY;
            } else {
                graphics.fill(chartLeft, barY, chartLeft + valueWidth, barY + 12, TOOLTIP_BASE);
            }
            graphics.drawString(this.font, String.format(Locale.ROOT, "%.2f", value.value()), chartLeft + valueWidth + 4, barY + 2, MUTED_TEXT);
            barY += 18;
        }
    }

    private void renderCodeBlock(final GuiGraphics graphics, final int x, final int y, final ManualContentLayout.CodeEntry codeEntry) {
        int lineY = y;
        if (!codeEntry.language().isBlank()) {
            graphics.drawString(this.font, "[" + codeEntry.language() + "]", x, lineY, TOOLTIP_HIGHLIGHT);
            lineY += lineStep();
        }
        for (String line : codeEntry.lines()) {
            graphics.drawString(this.font, line, x, lineY, TOOLTIP_BASE, false);
            lineY += lineStep();
        }
    }

    private void renderImageEntry(final GuiGraphics graphics, final int x, final int y, final int width, final ManualContentLayout.ImageEntry imageEntry) {
        if (imageEntry.kind() == g_mungus.zps.manual.markdown.ManualDocument.ImageKind.ITEM) {
            if (!imageEntry.stack().isEmpty()) {
                graphics.renderItem(imageEntry.stack(), x + 4, y + 4);
            }
            graphics.drawString(this.font, imageEntry.label(), x + 30, y + 8, TOOLTIP_BASE);
            return;
        }
        if (imageEntry.kind() == g_mungus.zps.manual.markdown.ManualDocument.ImageKind.RESOURCE) {
            try {
                final ResourceLocation texture = ResourceLocation.parse(imageEntry.target());
                graphics.blit(texture, x + 4, y + 4, 0, 0, Math.min(imageEntry.width() - 8, width - 8), imageEntry.height(this.font) - 8, Math.min(imageEntry.width() - 8, width - 8), imageEntry.height(this.font) - 8);
                graphics.drawString(this.font, imageEntry.label(), x + 8, y + imageEntry.height(this.font) - this.font.lineHeight - 2, TOOLTIP_HIGHLIGHT);
                return;
            } catch (Exception ignored) {
            }
        }
        graphics.drawString(this.font, imageEntry.label(), x + 6, y + 8, TOOLTIP_HIGHLIGHT);
        graphics.drawString(this.font, imageEntry.target(), x + 6, y + 22, TOOLTIP_BASE);
        graphics.drawString(this.font, "Image preview unavailable", x + 6, y + 36, MUTED_TEXT);
    }

    private void renderTable(final GuiGraphics graphics, final int x, final int y, final ManualContentLayout.TableEntry tableEntry) {
        int rowY = y;
        for (int rowIndex = 0; rowIndex < tableEntry.rows().size(); rowIndex++) {
            final ManualContentLayout.TableRowLayout row = tableEntry.rows().get(rowIndex);
            int cellX = x;
            for (int columnIndex = 0; columnIndex < tableEntry.columns(); columnIndex++) {
                final int cellLeft = cellX;
                final int cellRight = cellLeft + tableEntry.cellWidth();
                final int background = rowIndex == 0 ? TABLE_HEADER_BACKGROUND : TABLE_ROW_BACKGROUND;
                graphics.fill(cellLeft, rowY, cellRight, rowY + row.height(), background);
                graphics.fill(cellLeft, rowY, cellRight, rowY + 1, rowIndex == 0 ? TOOLTIP_HIGHLIGHT : TOOLTIP_BASE);
                if (columnIndex < row.cells().size()) {
                final List<FormattedCharSequence> lines = row.cells().get(columnIndex);
                renderAlignedLines(graphics, cellLeft + 4, cellRight - 4, rowY + 4, lines, columnIndex < tableEntry.alignments().size() ? tableEntry.alignments().get(columnIndex) : g_mungus.zps.manual.markdown.ManualDocument.TableAlignment.LEFT);
            }
                cellX += tableEntry.cellWidth() + 2;
            }
            rowY += row.height();
        }
    }

    private void renderAlignedLines(final GuiGraphics graphics, final int left, final int right, final int y, final List<FormattedCharSequence> lines, final g_mungus.zps.manual.markdown.ManualDocument.TableAlignment alignment) {
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            final FormattedCharSequence line = lines.get(lineIndex);
            final int lineWidth = this.font.width(line);
            final int x = switch (alignment) {
                case CENTER -> left + Math.max(0, (right - left - lineWidth) / 2);
                case RIGHT -> Math.max(left, right - lineWidth);
                case LEFT -> left;
            };
            graphics.drawString(this.font, line, x, y + lineIndex * lineStep(), rowColor(alignment), false);
        }
    }

    private int rowColor(final g_mungus.zps.manual.markdown.ManualDocument.TableAlignment alignment) {
        return switch (alignment) {
            case CENTER -> TOOLTIP_HIGHLIGHT;
            case LEFT, RIGHT -> TOOLTIP_BASE;
        };
    }

    private void drawLine(final GuiGraphics graphics, final int x1, final int y1, final int x2, final int y2, final int color) {
        final int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps; step++) {
            final int x = x1 + (x2 - x1) * step / Math.max(1, steps);
            final int y = y1 + (y2 - y1) * step / Math.max(1, steps);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void renderScrollBar(final GuiGraphics graphics, final int x, final int top, final int bottom) {
        graphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, SCROLLBAR_TRACK);
        if (this.maxScroll <= 0) {
            graphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, SCROLLBAR_THUMB);
            return;
        }
        final ScrollbarMetrics metrics = this.getScrollbarMetrics();
        final int thumbColor = this.draggingScrollbar ? SCROLLBAR_THUMB_ACTIVE : SCROLLBAR_THUMB;
        graphics.fill(x, metrics.handleTop(), x + SCROLLBAR_WIDTH, metrics.handleBottom(), thumbColor);
    }

    private void renderTextLines(final GuiGraphics graphics, final int x, final int y, final List<FormattedCharSequence> lines, final int scale) {
        if (scale == 1) {
            for (int index = 0; index < lines.size(); index++) {
                graphics.drawString(this.font, lines.get(index), x, y + index * lineStep(), BODY_TEXT, false);
            }
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(this.font, lines.get(index), x / scale, (y / scale) + index * ManualContentLayout.lineStep(this.font), BODY_TEXT, false);
        }
        graphics.pose().popPose();
    }

    private Style getStyleAt(final double mouseX, final double mouseY) {
        final int left = this.contentLeft();
        final int top = this.contentTop();
        final int localX = (int) mouseX - left;
        final int documentY = (int) mouseY - top + this.visibleScrollAmount();
        for (ManualContentLayout.Entry entry : this.cachedLayout.entries()) {
            final int startY = entry.y();
            final int endY = startY + entry.height(this.font);
            if (documentY < startY || documentY >= endY) {
                continue;
            }
            if (entry instanceof ManualContentLayout.TextEntry textEntry) {
                final int relativeY = documentY - startY;
                final int lineIndex = Math.min(textEntry.lines().size() - 1, Math.max(0, relativeY / scaledLineStep(textEntry.scale())));
                final FormattedCharSequence line = textEntry.lines().get(lineIndex);
                final int x = Math.max(0, localX - textEntry.xOffset());
                return this.font.getSplitter().componentStyleAtWidth(line, x / textEntry.scale());
            }
            if (entry instanceof ManualContentLayout.ListEntry listEntry) {
                final int relativeY = documentY - startY;
                final int lineIndex = Math.min(listEntry.lines().size() - 1, Math.max(0, relativeY / lineStep()));
                final FormattedCharSequence line = listEntry.lines().get(lineIndex);
                final int x = Math.max(0, localX - listEntry.indent() - 12);
                return this.font.getSplitter().componentStyleAtWidth(line, x);
            }
        }
        return null;
    }

    private void openDocument(final String target) {
        for (int index = 0; index < this.sections.size(); index++) {
            if (this.sections.get(index).documentPath().equals(target)) {
                this.selectedSectionIndex = index;
                this.rebuildWidgets();
                this.loadSelectedDocument();
                return;
            }
        }
    }

    private boolean isMouseOverTabs(final double mouseX, final double mouseY) {
        return mouseX >= this.frameLeft() + 4
            && mouseX <= this.contentLeft() - 16
            && mouseY >= this.frameTop() + 34
            && mouseY <= this.frameTop() + 34 + this.tabAreaHeight();
    }

    private boolean isMouseOverContent(final double mouseX, final double mouseY) {
        return mouseX >= this.contentLeft()
            && mouseX <= this.contentLeft() + this.contentWidth()
            && mouseY >= this.contentTop()
            && mouseY <= this.contentTop() + this.contentHeight();
    }

    private boolean isMouseOverScrollbar(final double mouseX, final double mouseY) {
        return mouseX >= this.scrollbarLeft()
            && mouseX <= this.scrollbarLeft() + SCROLLBAR_WIDTH
            && mouseY >= this.contentTop()
            && mouseY <= this.contentTop() + this.contentHeight();
    }

    private void updateSmoothScroll() {
        this.renderedScrollAmount = Mth.lerp(0.35D, this.renderedScrollAmount, this.scrollAmount);
        if (Math.abs(this.renderedScrollAmount - this.scrollAmount) < 0.5D) {
            this.renderedScrollAmount = this.scrollAmount;
        }
    }

    private int visibleScrollAmount() {
        return Mth.floor(this.renderedScrollAmount);
    }

    private int lineStep() {
        return ManualContentLayout.lineStep(this.font);
    }

    private int scaledLineStep(final int scale) {
        return ManualContentLayout.scaledLineStep(this.font, scale);
    }

    private int scrollbarLeft() {
        return this.contentLeft() + this.contentWidth() + 4;
    }

    private ScrollbarMetrics getScrollbarMetrics() {
        final int top = this.contentTop();
        final int height = this.contentHeight();
        if (this.maxScroll <= 0) {
            return new ScrollbarMetrics(top, height, 0);
        }
        final int handleHeight = Math.max(16, (int) ((float) height * height / Math.max(height, this.cachedLayout.totalHeight())));
        final int travel = height - handleHeight;
        final int handleTop = top + Math.round(travel * (float) (this.renderedScrollAmount / this.maxScroll));
        return new ScrollbarMetrics(handleTop, handleHeight, travel);
    }

    private void updateScrollFromScrollbar(final double mouseY) {
        final ScrollbarMetrics metrics = this.getScrollbarMetrics();
        if (metrics.travel() <= 0 || this.maxScroll <= 0) {
            this.scrollAmount = 0;
            this.renderedScrollAmount = 0;
            return;
        }
        final int minTop = this.contentTop();
        final int maxTop = minTop + metrics.travel();
        final int targetTop = Mth.clamp((int) Math.round(mouseY) - this.scrollbarDragOffset, minTop, maxTop);
        final float progress = (targetTop - minTop) / (float) metrics.travel();
        this.scrollAmount = Math.round(progress * this.maxScroll);
        this.renderedScrollAmount = this.scrollAmount;
    }

    private static int clampScroll(final int value, final int max) {
        return Math.max(0, Math.min(value, max));
    }

    private record ScrollbarMetrics(int handleTop, int handleHeight, int travel) {
        private int handleBottom() {
            return this.handleTop + this.handleHeight;
        }
    }

    private final class TabButton extends Button {
        private final ManualSection section;
        private final int index;

        private TabButton(final int x, final int y, final int width, final int height, final ManualSection section, final int index) {
            super(x, y, width, height, section.title(), button -> {
                ManualScreen.this.selectedSectionIndex = index;
                ManualScreen.this.rebuildWidgets();
                ManualScreen.this.loadSelectedDocument();
            }, DEFAULT_NARRATION);
            this.section = section;
            this.index = index;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.getY() + this.getHeight() < ManualScreen.this.frameTop() + 34 || this.getY() > ManualScreen.this.frameTop() + 34 + ManualScreen.this.tabAreaHeight()) {
                return;
            }
            final boolean expanded = this.isHoveredOrFocused() && this.isLabelTruncated();
            final int maxExpandedWidth = ManualScreen.this.frameLeft() + ManualScreen.this.frameWidth() - 20 - this.getX();
            final int expandedWidth = expanded
                ? Math.min(maxExpandedWidth, Math.max(this.getWidth(), ManualScreen.this.font.width(this.getMessage()) + 12))
                : this.getWidth();
            final int color = this.index == ManualScreen.this.selectedSectionIndex ? TAB_SELECTED : (this.isHoveredOrFocused() ? TAB_HOVER : TAB_NORMAL);
            graphics.fill(this.getX(), this.getY(), this.getX() + expandedWidth, this.getY() + this.getHeight(), color);
            graphics.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.getHeight(), this.index == ManualScreen.this.selectedSectionIndex ? TOOLTIP_HIGHLIGHT : TOOLTIP_BASE);
            graphics.drawString(ManualScreen.this.font, expanded ? this.getMessage() : this.getRenderedLabel(), this.getX() + 6, this.getY() + 7, this.index == ManualScreen.this.selectedSectionIndex ? TOOLTIP_HIGHLIGHT : TOOLTIP_BASE);
        }

        private Component getRenderedLabel() {
            final String label = this.getMessage().getString();
            final int maxWidth = this.getWidth() - 12;
            if (ManualScreen.this.font.width(label) <= maxWidth) {
                return Component.literal(label);
            }
            final String ellipsis = "...";
            final String clipped = ManualScreen.this.font.plainSubstrByWidth(label, Math.max(0, maxWidth - ManualScreen.this.font.width(ellipsis)));
            return Component.literal(clipped + ellipsis);
        }

        private boolean isLabelTruncated() {
            return ManualScreen.this.font.width(this.getMessage()) > this.getWidth() - 12;
        }
    }
}
