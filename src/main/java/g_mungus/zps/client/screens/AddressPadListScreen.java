package g_mungus.zps.client.screens;

import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.AddressPadRemovePositionC2SPacket;
import g_mungus.zps.networking.AddressPadSetEntriesC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    private static List<AddressPadItem.Entry> copiedEntries = List.of();

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
        this.listStartX = this.width / 2 - 150;
        this.listStartY = 50;
        int buttonSize = 20;
        int spacing = 4;
        int columnX = this.width / 2 + 132;
        int topY = this.listStartY;

        this.copyButton = this.addRenderableWidget(Button.builder(Component.literal("C"), button -> copyEntries())
                .bounds(columnX, topY, buttonSize, buttonSize)
                .build());
        this.pasteButton = this.addRenderableWidget(Button.builder(Component.literal("P"), button -> pasteEntries())
                .bounds(columnX, topY + (buttonSize + spacing), buttonSize, buttonSize)
                .build());
        this.clearButton = this.addRenderableWidget(Button.builder(Component.literal("R"), button -> clearEntries())
                .bounds(columnX, topY + (buttonSize + spacing) * 2, buttonSize, buttonSize)
                .build());
        this.closeButton = this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
                .bounds(columnX, topY + (buttonSize + spacing) * 3, buttonSize, buttonSize)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, 24, 0xFFFFFF);

        List<AddressPadItem.Entry> entries = getEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, EMPTY, this.width / 2, this.height / 2, 0xA0A0A0);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int startX = this.listStartX;
        int startY = this.listStartY;
        int rowHeight = 12;
        int color = 0xE0E0E0;
        int hoverColor = 0xFF8080;

        for (int i = 0; i < entries.size(); i++) {
            AddressPadItem.Entry entry = entries.get(i);
            String label = format(entry);
            int y = startY + i * rowHeight;
            int width = this.font.width(label);
            boolean hovered = mouseX >= startX && mouseX <= startX + width && mouseY >= y && mouseY < y + rowHeight;

            if (hovered) {
                graphics.drawString(this.font, Component.literal(label).withStyle(style -> style.withStrikethrough(true)), startX, y, hoverColor);
            } else {
                graphics.drawString(this.font, label, startX, y, color);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.copyButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, COPY_TOOLTIP, mouseX, mouseY);
        } else if (this.pasteButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, PASTE_TOOLTIP, mouseX, mouseY);
        } else if (this.clearButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, CLEAR_TOOLTIP, mouseX, mouseY);
        } else if (this.closeButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, CLOSE_TOOLTIP, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<AddressPadItem.Entry> entries = getEntries();
        int startX = this.listStartX;
        int startY = this.listStartY;
        int rowHeight = 12;
        for (int i = 0; i < entries.size(); i++) {
            AddressPadItem.Entry entry = entries.get(i);
            String label = format(entry);
            int y = startY + i * rowHeight;
            int width = this.font.width(label);
            boolean hovered = mouseX >= startX && mouseX <= startX + width && mouseY >= y && mouseY < y + rowHeight;
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
        return entry.name() + " - (" + entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ() + ")";
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
