package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.AirCompressorBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.menu.AirCompressorMenu;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.*;

/**
 * The Air Compressor's screen: the Vaporizer's, less the machine temperature and the ingredient
 * slots. The glass-fronted gas window sits in the middle of the dark upper panel and the FE bar
 * down its right-hand edge. Hovering the window lists what it holds.
 */
public class AirCompressorScreen extends AbstractContainerScreen<AirCompressorMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/air_compressor.png");

    // The panel is dark, so labels on it are light.
    private static final int PANEL_TEXT_COLOR = 0xC8D0E0;

    private static final int ENERGY_BAR_X = 152;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_WIDTH = 11;
    private static final int ENERGY_BAR_HEIGHT = 53;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    // The glass border and the dark window inside it, centred against the FE bar.
    private static final int GAS_BOX_X = 61;
    private static final int GAS_BOX_Y = 25;
    private static final int GAS_BOX_WIDTH = 54;
    private static final int GAS_BOX_HEIGHT = 33;
    private static final int GAS_WINDOW_X = GAS_BOX_X + 1;
    private static final int GAS_WINDOW_Y = GAS_BOX_Y + 1;
    private static final int GAS_WINDOW_WIDTH = GAS_BOX_WIDTH - 2;
    private static final int GAS_WINDOW_HEIGHT = GAS_BOX_HEIGHT - 2;
    /** The gas tint, without alpha: how full the receiver is sets how opaque it is drawn. */
    private static final int GAS_FILL_RGB = 0x3A8CB0;
    private static final int GAS_FILL_MAX_ALPHA = 0xB0;
    /**
     * The glass streak highlights inside the window, as texture coordinates. They sit on the
     * glass, not in the gas, so they are restored on top of the tint.
     */
    private static final int[][] GLASS_STREAK_PIXELS = {
            {65, 27}, {64, 28}, {63, 29},
            {112, 54}, {111, 55},
    };

    public AirCompressorScreen(AirCompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderEnergyTooltip(graphics, mouseX, mouseY);
        renderGasTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Gas fills the whole window at once and gets denser as the receiver fills, rather than
        // rising like a liquid.
        int gasAlpha = getGasAlpha();
        if (gasAlpha > 0) {
            graphics.fill(x + GAS_WINDOW_X, y + GAS_WINDOW_Y,
                    x + GAS_WINDOW_X + GAS_WINDOW_WIDTH, y + GAS_WINDOW_Y + GAS_WINDOW_HEIGHT,
                    (gasAlpha << 24) | GAS_FILL_RGB);
            for (int[] pixel : GLASS_STREAK_PIXELS) {
                graphics.blit(TEXTURE, x + pixel[0], y + pixel[1], pixel[0], pixel[1], 1, 1);
            }
        }

        int energyFill = getEnergyFill();
        if (energyFill > 0) {
            int fillTop = y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - energyFill;
            graphics.fill(x + ENERGY_BAR_X, fillTop, x + ENERGY_BAR_X + ENERGY_BAR_WIDTH,
                    y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, ENERGY_COLOR);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // The inventory label would land on the seam between the two panels, so only the title.
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, PANEL_TEXT_COLOR, false);
    }

    private void renderEnergyTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.literal(formatEnergy(menu.getEnergyStored(), menu.getMaxEnergyStored())), mouseX, mouseY);
        }
    }

    private void renderGasTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isHovering(GAS_BOX_X, GAS_BOX_Y, GAS_BOX_WIDTH, GAS_BOX_HEIGHT, mouseX, mouseY)) {
            return;
        }
        AirCompressorBlockEntity compressor = menu.getBlockEntity();
        List<Component> lines = new ArrayList<>();

        Map<ResourceLocation, Double> gases = new LinkedHashMap<>(compressor.getClientGasMasses());
        gases.values().removeIf(mass -> mass <= 0.001);
        if (gases.isEmpty()) {
            lines.add(Component.translatable("gui.zps.vaporizer.gas_empty"));
        } else {
            for (Map.Entry<ResourceLocation, Double> entry : gases.entrySet()) {
                lines.add(Component.translatable("gui.zps.vaporizer.gas_line",
                        gasName(entry.getKey()), formatMass(entry.getValue())));
            }
            lines.add(Component.translatable("gui.zps.vaporizer.gas_temperature",
                    GasGaugeBlockEntity.Mode.TEMPERATURE.format(compressor.getTemperature())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.zps.vaporizer.gas_pressure",
                    GasGaugeBlockEntity.Mode.PRESSURE.format(compressor.getPressure())).withStyle(ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private static Component gasName(ResourceLocation id) {
        GasType gas = GasTypeRegistry.INSTANCE.getGasType(id);
        return Component.literal(gas != null ? gas.getName() : id.toString());
    }

    /** How opaque to draw the gas: proportional to how full the receiver is against its cutoff. */
    private int getGasAlpha() {
        AirCompressorBlockEntity compressor = menu.getBlockEntity();
        double ceiling = compressor.getMaxPressure() * AirCompressorBlockEntity.FULL_FRACTION;
        if (ceiling <= 0) {
            return 0;
        }
        double fraction = compressor.getPressure() / ceiling;
        if (fraction <= 0) {
            return 0;
        }
        // Anything at all shows as at least a faint haze, so a nearly empty receiver is not mistaken for one.
        return Mth.clamp((int) Math.ceil(fraction * GAS_FILL_MAX_ALPHA), 0x10, GAS_FILL_MAX_ALPHA);
    }

    private int getEnergyFill() {
        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }

    private static String formatMass(double kg) {
        if (kg < 0.01) {
            return String.format(Locale.ROOT, "%.1f g", kg * 1000);
        }
        return String.format(Locale.ROOT, "%.2f kg", kg);
    }

    private static String formatEnergy(int stored, int max) {
        return NumberFormatter.formatInt(stored) + " / " + NumberFormatter.formatInt(max) + " FE";
    }
}
