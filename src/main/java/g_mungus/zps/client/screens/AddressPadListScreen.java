package g_mungus.zps.client.screens;

import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.AddressPadRemovePositionC2SPacket;
import g_mungus.zps.networking.AddressPadSetEntriesC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddressPadListScreen extends Screen {
    private static final Component TITLE = Component.literal("Saved Addresses");
    private static final Component EMPTY = Component.literal("No saved entries");
    private static final Component COPY_TOOLTIP = Component.literal("Copy entries");
    private static final Component PASTE_TOOLTIP = Component.literal("Paste entries");
    private static final Component CLEAR_TOOLTIP = Component.literal("Clear entries");
    private static final Component CLOSE_TOOLTIP = Component.literal("Close");
    private static final int DISPLAY_NAME_MAX = 15;
    private static final String NAME_ELLIPSIS = " . . .";
    private static List<AddressPadItem.Entry> copiedEntries = List.of();

    public static final ResourceLocation PONDER_WIDGETS_LOC = ResourceLocation.fromNamespaceAndPath("ponder", "textures/gui/widgets.png");
    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("zps", "textures/gui/address_pad_list_panel.png");
    private static final int ROW_HEIGHT = 12;
    private static final int LIST_PANEL_WIDTH = 212;
    private static final int LIST_PANEL_HORIZONTAL_PADDING = 16;
    private static final int LIST_PANEL_VERTICAL_PADDING = 24;
    private static final int TITLE_OFFSET_Y = 22;
    private static final int LIST_OFFSET_Y = 48;

    private final InteractionHand hand;
    private Button copyButton;
    private Button pasteButton;
    private Button clearButton;
    private Button closeButton;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int titleY;
    private int listStartX;
    private int listStartY;

    public AddressPadListScreen(InteractionHand hand) {
        super(GameNarrator.NO_TITLE);
        this.hand = hand;
    }

    @Override
    protected void init() {
        this.panelHeight = LIST_OFFSET_Y + (AddressPadItem.MAX_ENTRIES * ROW_HEIGHT) + LIST_PANEL_VERTICAL_PADDING;
        this.panelX = (this.width - LIST_PANEL_WIDTH) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.titleY = this.panelY + TITLE_OFFSET_Y;
        this.listStartX = this.panelX + LIST_PANEL_HORIZONTAL_PADDING;
        this.listStartY = this.panelY + LIST_OFFSET_Y;
        int buttonSize = 20;
        int spacing = 4;
        int columnX = this.panelX + LIST_PANEL_WIDTH + 8;
        int topY = this.listStartY;

        this.copyButton = this.addRenderableWidget(Button.builder(Component.literal("C"), button -> copyEntries())
                .bounds(columnX, topY, buttonSize, buttonSize)
                .build());
        this.pasteButton = this.addRenderableWidget(Button.builder(Component.literal("V"), button -> pasteEntries())
                .bounds(columnX, topY + (buttonSize + spacing), buttonSize, buttonSize)
                .build());
        this.clearButton = this.addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> clearEntries())
                .bounds(columnX, topY + (buttonSize + spacing) * 2, buttonSize, buttonSize)
                .build());
        this.closeButton = this.addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> this.onClose())
                .bounds(columnX, topY + (buttonSize + spacing) * 3, buttonSize, buttonSize)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderTexturedPanel(graphics);
        graphics.drawString(this.font, TITLE, this.width / 2 - this.font.width(TITLE) / 2, this.titleY, 0x303030, false);

        List<AddressPadItem.Entry> entries = getEntries();
        if (entries.isEmpty()) {
            graphics.drawString(this.font, EMPTY, this.width / 2 - this.font.width(EMPTY) / 2, this.listStartY, 0x808080, false);
        } else {
            int startX = this.listStartX;
            int coordsRightX = this.panelX + LIST_PANEL_WIDTH - LIST_PANEL_HORIZONTAL_PADDING;
            int startY = this.listStartY;
            int color = 0x303030;
            int hoverColor = 0xFF8080;

            for (int i = 0; i < entries.size(); i++) {
                AddressPadItem.Entry entry = entries.get(i);
                String nameText = formatName(entry.name());
                String coordsText = formatCoords(entry);
                int y = startY + i * ROW_HEIGHT;
                int coordsX = coordsRightX - this.font.width(coordsText);
                boolean hovered = mouseX >= startX && mouseX <= coordsRightX && mouseY >= y && mouseY < y + ROW_HEIGHT;

                if (hovered) {
                    graphics.drawString(this.font, Component.literal(nameText).withStyle(style -> style.withStrikethrough(true)), startX, y, hoverColor, false);
                    graphics.drawString(this.font, Component.literal(coordsText).withStyle(style -> style.withStrikethrough(true)), coordsX, y, hoverColor, false);
                } else {
                    graphics.drawString(this.font, nameText, startX, y, color, false);
                    graphics.drawString(this.font, coordsText, coordsX, y, color, false);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        PonderGuiTextures.ICON_CONFIG_DISCARD.render(graphics, this.clearButton.getX() + 2, this.clearButton.getY() + 2);
        PonderGuiTextures.ICON_DISABLE.render(graphics, this.closeButton.getX() + 2, this.closeButton.getY() + 2);

        if (this.copyButton.isHovered()) {
            graphics.renderTooltip(this.font, COPY_TOOLTIP, mouseX, mouseY);
        } else if (this.pasteButton.isHovered()) {
            graphics.renderTooltip(this.font, PASTE_TOOLTIP, mouseX, mouseY);
        } else if (this.clearButton.isHovered()) {
            graphics.renderTooltip(this.font, CLEAR_TOOLTIP, mouseX, mouseY);
        } else if (this.closeButton.isHovered()) {
            graphics.renderTooltip(this.font, CLOSE_TOOLTIP, mouseX, mouseY);
        }
    }

    private void renderTexturedPanel(GuiGraphics graphics) {
        graphics.blit(BACKGROUND_TEXTURE, this.panelX, this.panelY, 0, 0, LIST_PANEL_WIDTH, this.panelHeight, LIST_PANEL_WIDTH, this.panelHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<AddressPadItem.Entry> entries = getEntries();
        int startX = this.listStartX;
        int coordsRightX = this.panelX + LIST_PANEL_WIDTH - LIST_PANEL_HORIZONTAL_PADDING;
        int startY = this.listStartY;
        for (int i = 0; i < entries.size(); i++) {
            AddressPadItem.Entry entry = entries.get(i);
            int y = startY + i * ROW_HEIGHT;
            boolean hovered = mouseX >= startX && mouseX <= coordsRightX && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (!hovered) continue;

            ZPSGamePackets.INSTANCE.sendToServer(new AddressPadRemovePositionC2SPacket(hand, entry.name()));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<AddressPadItem.Entry> getEntries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return List.of();
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AddressPadItem)) return List.of();
        return AddressPadItem.getSortedEntries(stack);
    }

    private static String formatName(String name) {
        if (name.length() > DISPLAY_NAME_MAX) {
            name = name.substring(0, DISPLAY_NAME_MAX - 2) + NAME_ELLIPSIS;
        }
        return name;
    }

    private static String formatCoords(AddressPadItem.Entry entry) {
        return "(" + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ() + ")";
    }

    private void copyEntries() {
        copiedEntries = List.copyOf(getEntries());
    }

    private void pasteEntries() {
        if (copiedEntries.isEmpty()) return;
        ZPSGamePackets.INSTANCE.sendToServer(new AddressPadSetEntriesC2SPacket(this.hand, copiedEntries));
    }

    private void clearEntries() {
        ZPSGamePackets.INSTANCE.sendToServer(new AddressPadSetEntriesC2SPacket(this.hand, List.of()));
    }
}
