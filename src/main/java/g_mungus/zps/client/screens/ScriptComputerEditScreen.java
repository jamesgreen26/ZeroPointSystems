package g_mungus.zps.client.screens;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ScriptComputerEditScreen extends Screen {

    private static final Component SET_COMMAND_LABEL = Component.literal("Script Computer");
    private static final Component COMMAND_LABEL = Component.literal("ZPS Script Command");

    protected Button doneButton;
    protected Button cancelButton;
    protected EditBox commandEdit;

    public ScriptComputerEditScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        this.doneButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, arg -> this.onDone()).bounds(this.width / 2 - 4 - 150, this.height / 4 + 120 + 12, 150, 20).build()
        );
        this.cancelButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, arg -> this.onClose()).bounds(this.width / 2 + 4, this.height / 4 + 120 + 12, 150, 20).build()
        );

        this.commandEdit = new EditBox(this.font, this.width / 2 - 150, 50, 300, this.height / 4 + 70, Component.translatable("advMode.command"));
        this.commandEdit.setMaxLength(32500);
        this.commandEdit.setResponder(this::onEdited);
        this.addWidget(this.commandEdit);
        this.setInitialFocus(this.commandEdit);
    }

    protected void onDone() {
        final Minecraft client = this.minecraft;
        if (client != null) client.setScreen(null);
    }

    private void onEdited(String string) {

    }


    @Override
    public void render(@NotNull GuiGraphics arg, int i, int j, float f) {
        this.renderBackground(arg);
        arg.drawCenteredString(this.font, SET_COMMAND_LABEL, this.width / 2, 20, 16777215);
        arg.drawString(this.font, COMMAND_LABEL, this.width / 2 - 150, 40, 10526880);
        this.commandEdit.render(arg, i, j, f);

        super.render(arg, i, j, f);

    }
}
