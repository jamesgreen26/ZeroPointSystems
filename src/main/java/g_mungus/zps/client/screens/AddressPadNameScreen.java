package g_mungus.zps.client.screens;

import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.AddressPadAddPositionC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

public class AddressPadNameScreen extends Screen {
    private static final Component TITLE = Component.literal("Name Address");
    private static final Component LABEL = Component.literal("Address Name");

    private final InteractionHand hand;
    private final BlockPos pos;
    private EditBox nameField;
    private Button doneButton;

    public AddressPadNameScreen(InteractionHand hand, BlockPos pos) {
        super(GameNarrator.NO_TITLE);
        this.hand = hand;
        this.pos = pos;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, LABEL);
        this.nameField.setMaxLength(64);
        this.nameField.setFilter(value -> value.isEmpty() || AddressPadItem.isValidName(value));
        this.nameField.setResponder(value -> this.doneButton.active = AddressPadItem.isValidName(value));
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.submit())
                .bounds(centerX - 100, centerY + 20, 98, 20)
                .build());
        this.doneButton.active = false;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(centerX + 2, centerY + 20, 98, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
//        graphics.drawCenteredString(this.font, TITLE, this.width / 2, this.height / 2 - 34, 0xFFFFFF);
        graphics.drawString(this.font, LABEL, this.width / 2 - 100, this.height / 2 - 24, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            this.submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String name = this.nameField.getValue().trim();
        if (!AddressPadItem.isValidName(name)) return;
        ZPSGamePackets.INSTANCE.sendToServer(new AddressPadAddPositionC2SPacket(this.hand, this.pos, name));
        this.onClose();
    }
}
