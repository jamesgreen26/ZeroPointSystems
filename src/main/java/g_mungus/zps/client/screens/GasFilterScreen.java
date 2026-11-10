package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.networking.GasFilterSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
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
 * Settings for the gas filter: which gases it holds back. The reactor port's screen without the
 * direction button, sharing its {@link GasFilterList}. Every toggle is sent straight away, so there
 * is nothing to confirm or discard.
 */
public class GasFilterScreen extends Screen implements GasFilterList.Host {

    private static final Component TITLE = Component.translatable("block.zps.gas_filter");
    private static final Component FILTER_LABEL = Component.translatable("gui.zps.reactor_port.filter");
    private static final Component SEARCH_HINT = Component.translatable("gui.zps.reactor_port.filter.search");
    private static final Component NO_GASES = Component.translatable("gui.zps.reactor_port.no_gases");
    private static final Component NO_MATCHES = Component.translatable("gui.zps.reactor_port.filter.no_matches");
    private static final Component NO_FILTER = Component.translatable("gui.zps.gas_filter.no_filter");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    private static final int SECTION_GAP = 12;
    private static final int SEARCH_TO_LIST_GAP = 4;
    private static final int MAX_GAS_ROWS = 8;
    private static final int MIN_GAS_ROWS = 3;

    private static final int LABEL_COLOUR = 0xA0A0A0;

    private final BlockPos blockPos;

    /** Every registered gas plus any the filter names that nobody registers any more, blocked first. */
    private final List<GasFilterList.Gas> gases = new ArrayList<>();
    private boolean opened;

    /** The filter as last sent; mirrored from the block when the screen opens. */
    private GasFilter filter = GasFilter.PASS_ALL;

    private String query = "";
    private GasFilterList gasList;

    private int titleY;
    private int filterLabelY;

    public GasFilterScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        GasFilterBlockEntity blockEntity = getBlockEntity();
        if (!opened) {
            opened = true;
            filter = blockEntity != null ? blockEntity.getFilter() : GasFilter.PASS_ALL;
            if (blockEntity != null) {
                collectGases();
            }
        }

        int aboveList = TITLE_TO_FIRST_LABEL_GAP + LABEL_TO_CONTROL_GAP;
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
        filterLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int listY = titleY + aboveList;

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
        gasList.setEmptyMessage(getBlockEntity() == null ? NO_FILTER : gases.isEmpty() ? NO_GASES : NO_MATCHES);
    }

    // --- GasFilterList.Host -----------------------------------------------------------------

    @Override
    public boolean isBlocked(ResourceLocation gas) {
        return filter.blocks(gas);
    }

    @Override
    public boolean atLimit() {
        return filter.blocked().size() >= GasFilter.MAX_GASES;
    }

    @Override
    public void toggle(ResourceLocation gas) {
        filter = filter.toggling(gas);
        ZPSGamePackets.sendToServer(new GasFilterSettingsC2SPacket(blockPos, filter));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, 0xFFFFFF);
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

    private @Nullable GasFilterBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof GasFilterBlockEntity blockEntity)) {
            return null;
        }
        return blockEntity;
    }
}
