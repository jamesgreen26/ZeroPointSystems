package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.light_pipe.ScriptComputer;
import g_mungus.zps.client.screens.components.MultiLineEditBox;
import g_mungus.zps.client.screens.components.MultiLineCommandSuggestions;
import g_mungus.zps.client.screens.components.ScriptDispatcherProvider;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScriptComputerEditScreen extends Screen {

    private static final Component SET_COMMAND_LABEL = Component.literal("Script Computer");
    private static final Component COMMAND_LABEL = Component.literal("ZPS Script Command");
    protected final @Nullable ScriptComputer computer;
    protected final boolean debug;
    private String initialCommand = null;
    private boolean initialLoop = false;

    protected Button doneButton;
    protected Button cancelButton;
    protected MultiLineEditBox commandEdit;
    MultiLineCommandSuggestions commandSuggestions;

    public ScriptComputerEditScreen(@Nullable ScriptComputer computer, boolean debug) {
        super(GameNarrator.NO_TITLE);

        this.computer = computer;
        this.debug = debug;
    }

    @Override
    public void tick() {
        this.commandEdit.tick();
        if (!debug) {
            Minecraft mc = minecraft;
            if (computer == null || mc == null || mc.player == null || !computer.canEdit(mc.player.position())) {
                this.onClose();
            }
        }
    }

    @Override
    protected void init() {
        this.doneButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, arg -> this.onDone()).bounds(this.width / 2 - 4 - 150, this.height / 4 + 120 + 12, 150, 20).build()
        );
        this.cancelButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, arg -> this.onClose()).bounds(this.width / 2 + 4, this.height / 4 + 120 + 12, 150, 20).build()
        );

        this.commandEdit = new MultiLineEditBox(this.font, this.width / 2 - 150, 50, 300, this.height / 4 + 70, Component.translatable("advMode.command"));
        this.commandEdit.setMaxLength(32500);
        // Use initialCommand if set (from S2C packet), otherwise get from computer
        if (initialCommand != null) {
            this.commandEdit.setValue(initialCommand);
        } else if (computer != null) {
            this.commandEdit.setValue(computer.getValue());
        }
        this.commandEdit.setResponder(this::onEdited);
        this.addWidget(this.commandEdit);
        this.setInitialFocus(this.commandEdit);

        this.commandSuggestions = new MultiLineCommandSuggestions(this.minecraft, new ScriptDispatcherProvider(this.minecraft), this, this.commandEdit, this.font, true, true, 0, 7, false, Integer.MIN_VALUE);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public void resize(@NotNull Minecraft arg, int i, int j) {
        String string = this.commandEdit.getValue();
        this.init(arg, i, j);
        this.commandEdit.setValue(string);
        this.commandSuggestions.updateCommandInfo();
    }

    protected void onDone() {
        final Minecraft client = this.minecraft;
        if (computer != null) {
            // Use initialLoop if it was set from the packet, otherwise get from computer
            boolean loopValue = initialCommand != null ? initialLoop : computer.getLoop();
            ZPSGamePackets.INSTANCE.sendToServer(new ScriptComputerC2SPacket(computer.getPos(), loopValue, commandEdit.getValue()));
        }
        if (client != null) client.setScreen(null);
    }

    private void onEdited(String string) {
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (this.commandSuggestions.keyPressed(i, j, k)) {
            return true;
        } else if (super.keyPressed(i, j, k)) {
            return true;
        } else if (i != 257 && i != 335) {
            return false;
        } else {
            this.onDone();
            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f) {
        return this.commandSuggestions.mouseScrolled(f) || super.mouseScrolled(d, e, f);
    }

    @Override
    public boolean mouseClicked(double d, double e, int i) {
        return this.commandSuggestions.mouseClicked(d, e, i) || super.mouseClicked(d, e, i);
    }

    @Override
    public void render(@NotNull GuiGraphics arg, int i, int j, float f) {
        this.renderBackground(arg);
        arg.drawCenteredString(this.font, SET_COMMAND_LABEL, this.width / 2, 20, 16777215);
        arg.drawString(this.font, COMMAND_LABEL, this.width / 2 - 150, 40, 10526880);
        this.commandEdit.render(arg, i, j, f);

        super.render(arg, i, j, f);
        this.commandSuggestions.render(arg, i, j);
    }

    public static void openWithData(BlockPos pos, String commandData, boolean loop) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (blockEntity instanceof ScriptComputer scriptComputer) {
            ScriptComputerEditScreen screen = new ScriptComputerEditScreen(scriptComputer, false);
            screen.initialCommand = commandData;
            screen.initialLoop = loop;
            minecraft.setScreen(screen);
        }
    }
}
