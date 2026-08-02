package g_mungus.zps.client.screens;

import g_mungus.zps.ModSounds;
import g_mungus.zps.blockentity.light_pipe.ScriptComputer;
import g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene.PonderCompatibleScreen;
import g_mungus.zps.client.screens.components.MultiLineEditBox;
import g_mungus.zps.client.screens.components.MultiLineCommandSuggestions;
import g_mungus.zps.client.screens.components.ScriptDispatcherProvider;
import g_mungus.zps.commands.api_impl.aliases.ScriptAliases;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.manual.ModManuals;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ScriptTerminalScreen extends PonderCompatibleScreen {
    private static final ItemStack MANUAL_BUTTON_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);

    private static final Component SET_COMMAND_LABEL = Component.literal("Script Terminal");
    private static final Component COMMAND_LABEL = Component.literal("ZPS Script Command");
    protected final @Nullable ScriptComputer computer;
    protected final boolean debug;
    private String initialCommand = null;
    private boolean initialLoop = false;
    private int initialDelay = 4;

    protected Button doneButton;
    protected Button cancelButton;
    protected Button modeButton;
    protected Button delayButton;
    protected Button manualButton;
    protected MultiLineEditBox commandEdit;
    MultiLineCommandSuggestions commandSuggestions;
    private @Nullable TerminalDraft localDraft;

    private boolean isRepeatMode = false;
    private int delayIndex = 1;
    private static final String[] DELAY_KEYS = {"2t", "4t", "8t", "16t"};
    private static final int[] DELAY_VALUES = {2, 4, 8, 16};
    private static final Component OPEN_MANUAL_LABEL = Component.translatable("zps.screen.open_manual");
    private Set<ResourceLocation> connectedBlocks = null;

    public ScriptTerminalScreen(@Nullable ScriptComputer computer, boolean debug) {
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
        if (isInPonder()) {
            this.connectedBlocks = Set.of();
        }

        this.restoreExecutionControls();

        this.doneButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, arg -> this.onDone()).bounds(this.width / 2 - 150 - 2, this.height / 4 + 120 + 12, 148, 20).build()
        );
        this.cancelButton = this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, arg -> this.onClose()).bounds(this.width / 2 + 4, this.height / 4 + 120 + 12, 148, 20).build()
        );

        // Delay button (cycles through 0t, 2t, 4t, 8t)
        this.delayButton = this.addRenderableWidget(
                Button.builder(Component.literal(DELAY_KEYS[delayIndex]), arg -> {
                    delayIndex = (delayIndex + 1) % DELAY_KEYS.length;
                    this.delayButton.setMessage(Component.literal(DELAY_KEYS[delayIndex]));
                }).bounds(this.width / 2 + 62, 25, 24, 20).build()
        );

        // Mode button (toggles between IMPULSE and REPEAT)
        this.modeButton = this.addRenderableWidget(
                Button.builder(Component.literal(isRepeatMode ? "REPEAT" : "IMPULSE"), arg -> {
                    isRepeatMode = !isRepeatMode;
                    this.modeButton.setMessage(Component.literal(isRepeatMode ? "REPEAT" : "IMPULSE"));
                }).bounds(this.width / 2 + 90, 25, 60, 20).build()
        );

        if (!isInPonder()) {
            this.manualButton = this.addRenderableWidget(new ManualButton(4, this.height - 24));
        } else {
            this.manualButton = null;
        }

        this.commandEdit = new MultiLineEditBox(this.font, this.width / 2 - 150, 50, 300, this.height / 4 + 70, Component.translatable("advMode.command"));
        this.commandEdit.setMaxLength(32500);
        this.restoreEditorState();
        this.commandEdit.setResponder(this::onEdited);
        this.addWidget(this.commandEdit);
        this.setInitialFocus(this.commandEdit);
        refreshActiveAddresses();

        this.commandSuggestions = new MultiLineCommandSuggestions(this.minecraft, new ScriptDispatcherProvider(this.minecraft), this, this.commandEdit, this.font, true, true, 0, 7, false, Integer.MIN_VALUE, connectedBlocks);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public void resize(@NotNull Minecraft arg, int i, int j) {
        this.captureDraft();
        this.init(arg, i, j);
        this.commandSuggestions.updateCommandInfo();
    }

    protected void onDone() {
        final Minecraft client = this.minecraft;
        if (computer != null) {
            // Use the current isRepeatMode and delay from the buttons
            int currentDelay = DELAY_VALUES[delayIndex];
            ZPSGamePackets.INSTANCE.sendToServer(new ScriptComputerC2SPacket(computer.getPos(), isRepeatMode, currentDelay, commandEdit.getValue()));
        }
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of());
        ValueOfOrLiteralArgumentType.setActiveExpressionAliasNames(Set.of());
        if (client != null) client.setScreen(null);
    }

    @Override
    public void onClose() {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of());
        ValueOfOrLiteralArgumentType.setActiveExpressionAliasNames(Set.of());
        super.onClose();
    }

    private void onEdited(String string) {
        refreshActiveAddresses();
        this.captureDraft();
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        refreshActiveAddresses();
        boolean soundPlayed = false;
        if (ZPSConfig.useKeyboardSounds() && i != 256) {
            Minecraft.getInstance().player.playSound(ModSounds.KEYSTROKE.get());
            soundPlayed = true;
        }
        if (this.commandSuggestions.keyPressed(i, j, k)) {
            if (!soundPlayed && ZPSConfig.useKeyboardSounds()) {
                Minecraft.getInstance().player.playSound(ModSounds.KEYSTROKE.get());
            }
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
        if (this.manualButton != null && this.manualButton.isHoveredOrFocused()) {
            arg.renderTooltip(this.font, OPEN_MANUAL_LABEL, i, j);
        }
        this.commandSuggestions.render(arg, i, j);
    }

    public static void openWithData(BlockPos pos, String commandData, boolean loop, int delay, Set<ResourceLocation> connectedBlocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (blockEntity instanceof ScriptComputer scriptComputer) {
            ScriptTerminalScreen screen = new ScriptTerminalScreen(scriptComputer, false);
            screen.initialCommand = commandData;
            screen.initialLoop = loop;
            screen.initialDelay = delay;
            screen.connectedBlocks = connectedBlocks;
            minecraft.setScreen(screen);
        }
    }

    private final class ManualButton extends Button {
        private ManualButton(final int x, final int y) {
            super(x, y, 20, 20, CommonComponents.EMPTY, arg -> {
                ScriptTerminalScreen.this.captureDraft();
                if (ScriptTerminalScreen.this.minecraft != null && ScriptTerminalScreen.this.minecraft.level != null) {
                    ModManuals.openScriptCommandsManual(ScriptTerminalScreen.this.minecraft.level);
                }
            }, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.renderItem(MANUAL_BUTTON_ICON, this.getX() + 2, this.getY() + 2);
        }
    }

    private void restoreExecutionControls() {
        if (this.localDraft != null) {
            this.isRepeatMode = this.localDraft.repeatMode();
            this.delayIndex = Mth.clamp(this.localDraft.delayIndex(), 0, DELAY_KEYS.length - 1);
            return;
        }

        if (this.initialCommand != null) {
            this.isRepeatMode = this.initialLoop;
            this.delayIndex = this.findDelayIndex(this.initialDelay);
            return;
        }

        if (this.computer != null) {
            this.isRepeatMode = this.computer.getLoop();
            this.delayIndex = this.findDelayIndex(this.computer.getDelay());
            return;
        }

        this.isRepeatMode = false;
        this.delayIndex = 1;
    }

    private void restoreEditorState() {
        if (this.localDraft != null) {
            this.commandEdit.setValue(this.localDraft.command());
            this.commandEdit.setCursorPosition(this.localDraft.cursorPosition());
            this.commandEdit.setHighlightPos(this.localDraft.cursorPosition());
            return;
        }

        if (this.initialCommand != null) {
            this.commandEdit.setValue(this.initialCommand);
            return;
        }

        if (this.computer != null) {
            this.commandEdit.setValue(this.computer.getValue());
        }
    }

    private void captureDraft() {
        if (this.commandEdit == null) {
            return;
        }
        this.localDraft = new TerminalDraft(
            this.commandEdit.getValue(),
            this.commandEdit.getCursorPosition(),
            this.isRepeatMode,
            this.delayIndex
        );
    }

    private int findDelayIndex(final int delay) {
        for (int i = 0; i < DELAY_VALUES.length; i++) {
            if (DELAY_VALUES[i] == delay) {
                return i;
            }
        }
        return 1;
    }

    private void refreshActiveAddresses() {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(this.computer != null ? this.computer.getAvailableAddressNames() : Set.of());
        if (this.commandEdit == null) {
            ValueOfOrLiteralArgumentType.setActiveExpressionAliasNames(Set.of());
        } else {
            ValueOfOrLiteralArgumentType.setActiveExpressionAliases(ScriptAliases.parse(this.commandEdit.getValue()).aliases());
        }
    }

    private record TerminalDraft(String command, int cursorPosition, boolean repeatMode, int delayIndex) {
    }
}
