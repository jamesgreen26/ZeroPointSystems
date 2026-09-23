package g_mungus.zps.client.screens;

import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.networking.ReactorPortSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
 * changes, the way the creative gas generator's screen does, so there is nothing to confirm or discard.
 */
public class ReactorPortScreen extends Screen implements GasFilterList.Host {

    private static final Component TITLE = Component.translatable("block.zps.reactor_port");
    private static final Component MODE_LABEL = Component.translatable("gui.zps.reactor_port.mode");
    private static final Component FILTER_LABEL = Component.translatable("gui.zps.reactor_port.filter");
    private static final Component SEARCH_HINT = Component.translatable("gui.zps.reactor_port.filter.search");
    private static final Component NO_GASES = Component.translatable("gui.zps.reactor_port.no_gases");
    private static final Component NO_MATCHES = Component.translatable("gui.zps.reactor_port.filter.no_matches");
    private static final Component NO_PORT = Component.translatable("gui.zps.reactor_port.no_port");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    /** Between the search box and the list under it. */
    private static final int SEARCH_TO_LIST_GAP = 4;
    /** The list shows this many rows before it scrolls, and keeps this many even when near empty. */
    private static final int MAX_GAS_ROWS = 6;
    private static final int MIN_GAS_ROWS = 3;

    private static final int LABEL_COLOUR = 0xA0A0A0;

    private final BlockPos blockPos;

    /**
     * Every gas anyone has registered, plus any the filter names that nobody registers any more.
     * Ordered once, when the screen opens — blocked first — so rows never move under the cursor.
     */
    private final List<GasFilterList.Gas> gases = new ArrayList<>();
    private boolean opened;

    /** The settings as last sent; mirrored from the block when the screen opens. */
    private ReactorPortMode mode = ReactorPortMode.INPUT;
    private GasFilter filter = GasFilter.PASS_ALL;

    private String query = "";
    private GasFilterList gasList;

    // Laid out in init(), read back in render() so labels track their controls.
    private int titleY;
    private int modeLabelY;
    private int filterLabelY;

    public ReactorPortScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        ReactorPortBlockEntity port = getBlockEntity();
        if (!opened) {
            opened = true;
            mode = port != null ? port.getMode() : ReactorPortMode.INPUT;
            filter = port != null ? port.getFilter() : GasFilter.PASS_ALL;
            if (port != null) {
                collectGases();
            }
        }

        // Everything above the list, which is fixed, then as many rows as the screen has room for.
        int aboveList = TITLE_TO_FIRST_LABEL_GAP
                + LABEL_TO_CONTROL_GAP + CONTROL_HEIGHT + SECTION_GAP
                + LABEL_TO_CONTROL_GAP;
        int searchHeight = CONTROL_HEIGHT + SEARCH_TO_LIST_GAP;
        int visibleRows = visibleRows(aboveList);
        // The search box is only worth its space once the list has to scroll.
        boolean searchable = gases.size() > visibleRows;
        if (searchable) {
            aboveList += searchHeight;
            visibleRows = visibleRows(aboveList);
        } else {
            query = "";
        }

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        int stackHeight = aboveList + GasFilterList.heightFor(visibleRows);
        titleY = Math.max(SECTION_GAP, this.height / 2 - stackHeight / 2);

        modeLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int modeButtonY = modeLabelY + LABEL_TO_CONTROL_GAP;
        filterLabelY = modeButtonY + CONTROL_HEIGHT + SECTION_GAP;
        int listY = titleY + aboveList;

        this.addRenderableWidget(Button.builder(modeButtonText(), button -> {
                    mode = mode.next();
                    button.setMessage(modeButtonText());
                    sendSettings();
                })
                .bounds(left, modeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());

        if (searchable) {
            EditBox search = new EditBox(this.font, left, listY - searchHeight, CONTROL_WIDTH, CONTROL_HEIGHT,
                    SEARCH_HINT);
            search.setMaxLength(64);
            search.setHint(SEARCH_HINT);
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                refreshList();
            });
            this.addRenderableWidget(search);
        }

        gasList = new GasFilterList(this.minecraft, left, listY, CONTROL_WIDTH, visibleRows, this);
        this.addRenderableWidget(gasList);
        refreshList();
    }

    /** How many rows the list gets: enough for the gases, within its bounds and the screen's. */
    private int visibleRows(int aboveList) {
        int room = this.height - SECTION_GAP - (SECTION_GAP + aboveList) - GasFilterList.heightFor(0);
        int fitting = Math.max(1, room / GasFilterList.ROW_HEIGHT);
        return Math.min(Mth.clamp(gases.size(), MIN_GAS_ROWS, MAX_GAS_ROWS), fitting);
    }

    private void collectGases() {
        gases.clear();
        for (GasType gas : GasTypeRegistry.INSTANCE.getGasTypes()) {
            gases.add(new GasFilterList.Gas(gas.getResourceLocation(), gas.getName(), gas.getIconLocation(), true));
        }
        // A gas whose mod has gone is still in the filter; without a row it could never be unblocked.
        for (ResourceLocation id : filter.blocked()) {
            if (GasTypeRegistry.INSTANCE.getGasType(id) == null) {
                gases.add(new GasFilterList.Gas(id, id.toString(), GasType.Companion.getPLACEHOLDER_ICON(), false));
            }
        }
        gases.sort(Comparator
                .comparing((GasFilterList.Gas gas) -> !filter.blocks(gas.id()))
                .thenComparing(GasFilterList.Gas::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(gas -> gas.id().toString()));
    }

    private void refreshList() {
        gasList.show(gases, query);
        gasList.setEmptyMessage(getBlockEntity() == null ? NO_PORT : gases.isEmpty() ? NO_GASES : NO_MATCHES);
    }

    // --- GasFilterList.Host -----------------------------------------------------------------

    @Override
    public boolean isBlocked(ResourceLocation gas) {
        return filter.blocks(gas);
    }

    /** The server throws away a whole settings packet that names too many gases, mode and all. */
    @Override
    public boolean atLimit() {
        return filter.blocked().size() >= GasFilter.MAX_GASES;
    }

    @Override
    public void toggle(ResourceLocation gas) {
        filter = filter.toggling(gas);
        sendSettings();
    }

    private void sendSettings() {
        ZPSGamePackets.sendToServer(new ReactorPortSettingsC2SPacket(blockPos, mode, filter));
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics p_333749_, int p_333882_, int p_333946_, float p_334094_) {
        this.renderTransparentBackground(p_333749_);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - CONTROL_WIDTH / 2;

        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, 0xFFFFFF);
        graphics.drawString(this.font, MODE_LABEL, left, modeLabelY, LABEL_COLOUR);
        graphics.drawString(this.font, FILTER_LABEL, left, filterLabelY, LABEL_COLOUR);
        if (!gases.isEmpty()) {
            Component count = Component.translatable("gui.zps.reactor_port.filter.count",
                    filter.blocked().size(), gases.size());
            graphics.drawString(this.font, count, left + CONTROL_WIDTH - this.font.width(count), filterLabelY,
                    LABEL_COLOUR);
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
