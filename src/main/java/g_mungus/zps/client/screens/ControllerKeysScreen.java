package g_mungus.zps.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.blockentity.DodecaControllerBlockEntity;
import g_mungus.zps.networking.ControllerKeysC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys of one controller, one button per channel, bound the way the controls menu binds: click a
 * channel, then press its key. Escape clears it. Every change is sent as it is made, so there is
 * nothing to confirm on the way out.
 *
 * <p>Channels are laid out one column per side the controller's cables leave from, as the rider
 * sees it — left, back and right for the Dodeca-Controller, down and back for the Octo-Controller —
 * each button's border drawn in the colour the Dense Cable Separator gives that channel. Keys bound to two channels
 * at once are shown in red, as the controls menu shows a clash.
 */
public class ControllerKeysScreen extends Screen {

    private static final Component RESET = Component.translatable("controls.resetAll");

    /** The Dense Cable Separator's channel colours, in channel order: green, blue, red, purple. */
    private static final int[] CHANNEL_COLOURS = {0xFF0C871F, 0xFF115E92, 0xFF870C28, 0xFF5D26A8};
    private static final int CHANNELS_PER_SIDE = 4;

    private static final int BUTTON_WIDTH = 90;
    /** Room for the side names between the title and the first row. */
    private static final int HEADER_HEIGHT = 14;
    private static final int COLUMN_GAP = 16;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_HEIGHT = 20;

    private final BlockPos blockPos;
    private final int[] keys;
    /** The name of each side, in channel order. */
    private final Component[] sides;
    /** Which column each side is shown in, in channel order. */
    private final int[] sideColumns;
    private final List<Button> keyButtons = new ArrayList<>();

    /** The channel waiting for a key, or -1. */
    private int listening = -1;

    private int titleY;
    private int firstRowY;

    public ControllerKeysScreen(BlockPos blockPos, ControllerBlockEntity<?> controller) {
        super(controller.getBlockState().getBlock().getName());
        this.blockPos = blockPos;
        this.keys = new int[controller.channelCount()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = controller.getKey(i);
        }
        // Channels A-D, E-H and I-L leave from one side each; the rider faces the block, so the
        // block's clockwise side is on their left.
        boolean dodeca = controller instanceof DodecaControllerBlockEntity;
        String[] sideKeys = dodeca ? new String[]{"left", "right", "back"} : new String[]{"down", "back"};
        // Shown left to right as the rider sees them, so back sits between left and right.
        this.sideColumns = dodeca ? new int[]{0, 2, 1} : new int[]{0, 1};
        this.sides = new Component[sideKeys.length];
        for (int i = 0; i < sideKeys.length; i++) {
            sides[i] = Component.translatable("zps.controller.side." + sideKeys[i]);
        }
    }

    private int columns() {
        return sides.length;
    }

    private int columnWidth() {
        return BUTTON_WIDTH;
    }

    private int columnOf(int index) {
        return sideColumns[index / CHANNELS_PER_SIDE];
    }

    private int totalWidth() {
        return columns() * columnWidth() + (columns() - 1) * COLUMN_GAP;
    }

    private int columnLeft(int column) {
        return this.width / 2 - totalWidth() / 2 + column * (columnWidth() + COLUMN_GAP);
    }

    private int rowY(int index) {
        return firstRowY + (index % CHANNELS_PER_SIDE) * ROW_HEIGHT;
    }

    @Override
    protected void init() {
        int totalHeight = 16 + HEADER_HEIGHT + CHANNELS_PER_SIDE * ROW_HEIGHT + 8 + CONTROL_HEIGHT;
        titleY = this.height / 2 - totalHeight / 2;
        firstRowY = titleY + 16 + HEADER_HEIGHT;

        keyButtons.clear();
        for (int i = 0; i < keys.length; i++) {
            int channel = i;
            int x = columnLeft(columnOf(i));
            keyButtons.add(this.addRenderableWidget(Button.builder(Component.empty(), button -> listening = channel)
                    .bounds(x, rowY(i), BUTTON_WIDTH, CONTROL_HEIGHT)
                    .build()));
        }

        int bottomY = firstRowY + CHANNELS_PER_SIDE * ROW_HEIGHT + 8;
        int bottomWidth = totalWidth();
        int half = (bottomWidth - 8) / 2;
        int left = columnLeft(0);
        this.addRenderableWidget(Button.builder(RESET, button -> {
                    listening = -1;
                    System.arraycopy(ControllerBlockEntity.defaultKeys(keys.length), 0, keys, 0, keys.length);
                    sendKeys();
                })
                .bounds(left, bottomY, half, CONTROL_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + bottomWidth - half, bottomY, half, CONTROL_HEIGHT)
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening >= 0) {
            keys[listening] = keyCode == GLFW.GLFW_KEY_ESCAPE ? ControllerBlockEntity.UNBOUND : keyCode;
            listening = -1;
            sendKeys();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only keys can be bound, so a click elsewhere while waiting for one just stops waiting.
        if (listening >= 0 && !keyButtons.get(listening).isMouseOver(mouseX, mouseY)) {
            listening = -1;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendKeys() {
        ZPSGamePackets.sendToServer(new ControllerKeysC2SPacket(blockPos, keys.clone()));
    }

    private boolean clashes(int index) {
        if (keys[index] == ControllerBlockEntity.UNBOUND) return false;
        for (int i = 0; i < keys.length; i++) {
            if (i != index && keys[i] == keys[index]) return true;
        }
        return false;
    }

    private Component keyName(int index) {
        InputConstants.Key key = keys[index] == ControllerBlockEntity.UNBOUND
                ? InputConstants.UNKNOWN
                : InputConstants.Type.KEYSYM.getOrCreate(keys[index]);
        MutableComponent name = key.getDisplayName().copy();
        if (index == listening) {
            return Component.literal("> ").append(name.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <").withStyle(ChatFormatting.YELLOW);
        }
        return clashes(index) ? name.withStyle(ChatFormatting.RED) : name;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        for (int i = 0; i < keyButtons.size(); i++) {
            keyButtons.get(i).setMessage(keyName(i));
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, title, this.width / 2, titleY, 0xFFFFFF);
        for (int side = 0; side < sides.length; side++) {
            int x = columnLeft(sideColumns[side]) + BUTTON_WIDTH / 2;
            graphics.drawCenteredString(this.font, sides[side], x, firstRowY - HEADER_HEIGHT, 0xA0A0A0);
        }
        // Over the button's own black border, which turns white when hovered or focused; that is kept.
        for (int i = 0; i < keyButtons.size(); i++) {
            Button button = keyButtons.get(i);
            if (!button.isHoveredOrFocused()) {
                graphics.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                        CHANNEL_COLOURS[i % CHANNELS_PER_SIDE]);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
