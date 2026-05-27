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
import net.minecraft.client.gui.components.ImageButton;
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
    private static final int DISPLAY_NAME_MAX = 16;
    private static final String NAME_ELLIPSIS = " . . .";
    private static List<AddressPadItem.Entry> copiedEntries = List.of();

    public static final ResourceLocation PONDER_WIDGETS_LOC = ResourceLocation.fromNamespaceAndPath("ponder", "textures/gui/widgets.png");
    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("zps", "textures/block/decor/riveted_space_plating.png");
    private static final int ROW_HEIGHT = 12;
    private static final int LIST_PANEL_WIDTH = 228;
    private static final int LIST_PANEL_TOP = 18;
    private static final int LIST_PANEL_PADDING = 8;

    private final InteractionHand hand;
    private Button copyButton;
    private Button pasteButton;
    private Button clearButton;
    private Button closeButton;
    private int listStartX;
    private int listStartY;

    public AddressPadListScreen(InteractionHand hand) {
        super(GameNarrator.NO_TITLE);
        this.hand = hand;
    }

    @Override
    protected void init() {
        this.listStartX = this.width / 2 - 112;
        this.listStartY = 50;
        int buttonSize = 20;
        int spacing = 4;
        int columnX = this.width / 2 + 94;
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
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, 24, 0xFFFFFF);

        List<AddressPadItem.Entry> entries = getEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, EMPTY, this.width / 2, this.height / 2, 0xA0A0A0);
        } else {
            int startX = this.listStartX;
            int startY = this.listStartY;
            int color = 0xE0E0E0;
            int hoverColor = 0xFF8080;

            for (int i = 0; i < entries.size(); i++) {
                AddressPadItem.Entry entry = entries.get(i);
                String label = format(entry);
                int y = startY + i * ROW_HEIGHT;
                int width = this.font.width(label);
                boolean hovered = mouseX >= startX && mouseX <= startX + width && mouseY >= y && mouseY < y + ROW_HEIGHT;

                if (hovered) {
                    graphics.drawString(this.font, Component.literal(label).withStyle(style -> style.withStrikethrough(true)), startX, y, hoverColor);
                } else {
                    graphics.drawString(this.font, label, startX, y, color);
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
        int panelX = this.listStartX - LIST_PANEL_PADDING;
        int panelY = LIST_PANEL_TOP;
        int panelHeight = (this.listStartY - panelY) + (AddressPadItem.MAX_ENTRIES * ROW_HEIGHT) + LIST_PANEL_PADDING;
        int tileSize = 16;
        for (int y = panelY; y < panelY + panelHeight; y += tileSize) {
            int drawHeight = Math.min(tileSize, panelY + panelHeight - y);
            for (int x = panelX; x < panelX + LIST_PANEL_WIDTH; x += tileSize) {
                int drawWidth = Math.min(tileSize, panelX + LIST_PANEL_WIDTH - x);
                graphics.blit(BACKGROUND_TEXTURE, x, y, 0, 0, drawWidth, drawHeight, tileSize, tileSize);
            }
        }

        graphics.fill(panelX, panelY, panelX + LIST_PANEL_WIDTH, panelY + panelHeight, 0x55000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<AddressPadItem.Entry> entries = getEntries();
        int startX = this.listStartX;
        int startY = this.listStartY;
        for (int i = 0; i < entries.size(); i++) {
            AddressPadItem.Entry entry = entries.get(i);
            String label = format(entry);
            int y = startY + i * ROW_HEIGHT;
            int width = this.font.width(label);
            boolean hovered = mouseX >= startX && mouseX <= startX + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
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

    private static String format(AddressPadItem.Entry entry) {
        String name = entry.name();
        if (name.length() > DISPLAY_NAME_MAX) {
            name = name.substring(0, 14) + NAME_ELLIPSIS;
        }
        return name + " - (" + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ() + ")";
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
