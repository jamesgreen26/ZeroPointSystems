package g_mungus.zps.client.screens;

import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.networking.ReactorPortSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Settings for the reactor port: which way it carries gas across the wall, and which gases it
 * holds back.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory, so there is
 * nothing for a menu to hold. Every control sends the whole settings block to the server as it
 * changes, the way the gas gauge's screen does, so there is nothing to confirm or discard.
 */
public class ReactorPortScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.reactor_port");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.reactor_port.mode");
    private static final Component FILTER_LABEL = Component.translatable("gui.zps.reactor_port.filter");
    private static final Component NO_GASES = Component.translatable("gui.zps.reactor_port.no_gases");
    private static final Component NO_PORT = Component.translatable("gui.zps.reactor_port.no_port");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    /** The gas toggles, two to a row. */
    private static final int GAS_COLUMNS = 2;
    private static final int GAS_ROW_HEIGHT = 20;

    private static final int LABEL_COLOUR = 0xA0A0A0;

    private final BlockPos blockPos;

    /** Every gas anyone has registered, in a stable order so the list does not shuffle. */
    private final List<GasType> gases = new ArrayList<>();

    /** The settings as last sent; mirrored from the block when the screen opens. */
    private ReactorPortMode mode = ReactorPortMode.INPUT;
    private GasFilter filter = GasFilter.PASS_ALL;

    // Laid out in init(), read back in render() so labels track their controls.
    private int titleY;
    private int modeLabelY;
    private int filterLabelY;
    private int gasesY;

    public ReactorPortScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        gases.clear();
        gases.addAll(GasTypeRegistry.INSTANCE.getGAS_TYPES().values());
        gases.sort(Comparator.comparing(gas -> gas.getResourceLocation().toString()));

        ReactorPortBlockEntity port = getBlockEntity();
        mode = port != null ? port.getMode() : ReactorPortMode.INPUT;
        filter = port != null ? port.getFilter() : GasFilter.PASS_ALL;

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        int gasRows = (gases.size() + GAS_COLUMNS - 1) / GAS_COLUMNS;
        int gasesHeight = Math.max(gasRows, 1) * GAS_ROW_HEIGHT;

        int stackHeight = TITLE_TO_FIRST_LABEL_GAP
                + LABEL_TO_CONTROL_GAP + CONTROL_HEIGHT + SECTION_GAP
                + LABEL_TO_CONTROL_GAP + gasesHeight;
        titleY = Math.max(SECTION_GAP, this.height / 2 - stackHeight / 2);

        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        filterLabelY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;
        gasesY = filterLabelY + LABEL_TO_CONTROL_GAP;

        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    button.setMessage(modeButtonText());
                    sendSettings();
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        int columnWidth = CONTROL_WIDTH / GAS_COLUMNS;
        for (int index = 0; index < gases.size(); index++) {
            GasType gas = gases.get(index);
            int x = left + (index % GAS_COLUMNS) * columnWidth;
            int y = gasesY + (index / GAS_COLUMNS) * GAS_ROW_HEIGHT;
            this.addRenderableWidget(new GasBlockToggle(x, y, columnWidth - 4, Component.literal(gas.getName()),
                    this.font, filter.blocks(gas.getResourceLocation()),
                    blocked -> {
                        filter = filter.toggling(gas.getResourceLocation());
                        sendSettings();
                    }));
        }
    }

    private void sendSettings() {
        ZPSGamePackets.sendToServer(new ReactorPortSettingsC2SPacket(blockPos, mode, filter));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - CONTROL_WIDTH / 2;

        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, 0xFFFFFF);
        graphics.drawString(this.font, MODE_LABEL, left, modeLabelY, LABEL_COLOUR);
        graphics.drawString(this.font, FILTER_LABEL, left, filterLabelY, LABEL_COLOUR);
        if (getBlockEntity() == null) {
            graphics.drawString(this.font, NO_PORT, left, gasesY, LABEL_COLOUR);
        } else if (gases.isEmpty()) {
            graphics.drawString(this.font, NO_GASES, left, gasesY, LABEL_COLOUR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component modeButtonText() {
        return Component.translatable(mode.translationKey());
    }

    private @Nullable ReactorPortBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof ReactorPortBlockEntity port)) return null;
        return port;
    }
}
