package g_mungus.zps.client.screens;

import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.AddressPadRemovePositionC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddressPadListScreen extends Screen {
    private static final Component TITLE = Component.literal("Saved Addresses");
    private static final Component EMPTY = Component.literal("No saved entries");

    private final InteractionHand hand;

    public AddressPadListScreen(InteractionHand hand) {
        super(GameNarrator.NO_TITLE);
        this.hand = hand;
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

        int startX = this.width / 2 - 150;
        int startY = 50;
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<AddressPadItem.Entry> entries = getEntries();
        int startX = this.width / 2 - 150;
        int startY = 50;
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
}
