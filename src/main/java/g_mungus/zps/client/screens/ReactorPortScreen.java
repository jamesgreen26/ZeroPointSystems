package g_mungus.zps.client.screens;

import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.networking.ReactorPortSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Settings for the reactor port: which way it carries gas across the wall, and which gases.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory, so there is
 * nothing for a menu to hold. Every control edits a local copy; nothing reaches the server until
 * Done is pressed, and Cancel or Escape throws the edits away. Done sends only if something
 * actually differs from what the block already has.
 */
public class ReactorPortScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.reactor_port");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.reactor_port.mode");
    private static final Component FILTER_LABEL = Component.translatable("gui.zps.reactor_port.filter");
    private static final Component NO_GASES = Component.translatable("gui.zps.reactor_port.no_gases");
    private static final Component NO_PORT = Component.translatable("gui.zps.reactor_port.no_port");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int HALF_WIDTH = (CONTROL_WIDTH - BUTTON_GAP) / 2;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    /** The gas checkboxes, two to a row. */
    private static final int GAS_COLUMNS = 2;
    private static final int GAS_ROW_HEIGHT = 20;

    private static final int LABEL_COLOUR = 0xA0A0A0;

    private final BlockPos blockPos;

    /** Every gas anyone has registered, in a stable order so the list does not shuffle. */
    private final List<GasType> gases = new ArrayList<>();

    /** The settings being chosen here; sent on Done, dropped on Cancel. */
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
                + LABEL_TO_CONTROL_GAP + CONTROL_HEIGHT + SECTION_GAP
                + gasesHeight + SECTION_GAP
                + CONTROL_HEIGHT;
        titleY = Math.max(SECTION_GAP, this.height / 2 - stackHeight / 2);

        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        filterLabelY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;
        int filterButtonY = filterLabelY + LABEL_TO_CONTROL_GAP;
        gasesY = filterButtonY + CONTROL_HEIGHT + SECTION_GAP;
        int actionsY = gasesY + gasesHeight + SECTION_GAP;

        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    button.setMessage(modeButtonText());
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(filterButtonText(), button -> {
                    filter = filter.withBlacklist(!filter.blacklist());
                    button.setMessage(filterButtonText());
                })
                .bounds(left, filterButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        int columnWidth = CONTROL_WIDTH / GAS_COLUMNS;
        for (int index = 0; index < gases.size(); index++) {
            GasType gas = gases.get(index);
            int x = left + (index % GAS_COLUMNS) * columnWidth;
            int y = gasesY + (index / GAS_COLUMNS) * GAS_ROW_HEIGHT;
            this.addRenderableWidget(Checkbox.builder(Component.literal(gas.getName()), this.font)
                    .pos(x, y)
                    .maxWidth(columnWidth - 4)
                    .selected(filter.gases().contains(gas.getResourceLocation()))
                    .onValueChange((checkbox, selected) -> filter = filter.toggling(gas.getResourceLocation()))
                    .build());
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
                    apply();
                    onClose();
                })
                .bounds(left, actionsY, HALF_WIDTH, CONTROL_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + HALF_WIDTH + BUTTON_GAP, actionsY, HALF_WIDTH, CONTROL_HEIGHT)
                .build());
    }

    /** Send the chosen settings, unless the block already has them. */
    private void apply() {
        ReactorPortBlockEntity port = getBlockEntity();
        if (port == null || (port.getMode() == mode && port.getFilter().equals(filter))) {
            return;
        }
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

    private Component filterButtonText() {
        return Component.translatable(filter.blacklist()
                ? "gui.zps.reactor_port.filter.blacklist"
                : "gui.zps.reactor_port.filter.whitelist");
    }

    private @Nullable ReactorPortBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof ReactorPortBlockEntity port)) return null;
        return port;
    }
}
