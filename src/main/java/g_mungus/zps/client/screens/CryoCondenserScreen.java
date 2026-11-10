package g_mungus.zps.client.screens;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.CryoCondenserBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.menu.CryoCondenserMenu;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Cryo Condenser's screen, laid out as the Vaporizer's: the machine temperature in the dark
 * panel's bottom-left corner, the glass-fronted gas buffer with the three output slots under it,
 * and the FE bar down the right-hand edge. Hovering the buffer lists what it holds.
 */
public class CryoCondenserScreen extends AbstractContainerScreen<CryoCondenserMenu> {
    private static final ResourceLocation TEXTURE = ZPSMod.resource("textures/gui/cryo_condenser.png");

    private static final int PANEL_TEXT_COLOR = 0xC8D0E0;

    private static final int ENERGY_BAR_X = 152;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_WIDTH = 11;
    private static final int ENERGY_BAR_HEIGHT = 53;
    private static final int ENERGY_COLOR = 0xFF2380A8;

    private static final int GAS_BOX_X = 61;
    private static final int GAS_BOX_Y = 15;
    private static final int GAS_BOX_WIDTH = 54;
    private static final int GAS_BOX_HEIGHT = 33;
    private static final int GAS_WINDOW_X = GAS_BOX_X + 1;
    private static final int GAS_WINDOW_Y = GAS_BOX_Y + 1;
    private static final int GAS_WINDOW_WIDTH = GAS_BOX_WIDTH - 2;
    private static final int GAS_WINDOW_HEIGHT = GAS_BOX_HEIGHT - 2;
    /** A paler, icier tint than the Vaporizer's. */
    private static final int GAS_FILL_RGB = 0x9CC8E0;
    private static final int GAS_FILL_MAX_ALPHA = 0xB0;
    /** The glass streak highlights inside the window, restored on top of the tint. */
    private static final int[][] GLASS_STREAK_PIXELS = {
            {65, 17}, {64, 18}, {63, 19},
            {112, 44}, {111, 45},
    };

    private static final int TEMPERATURE_X = 8;
    private static final int TEMPERATURE_Y = 61;
    private static final int TEMPERATURE_HOVER_WIDTH = 50;
    private static final int TEMPERATURE_HOVER_HEIGHT = 10;

    public CryoCondenserScreen(CryoCondenserMenu menu, Inventory inventory, Component title) {
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
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderEnergyTooltip(graphics, mouseX, mouseY);
        renderGasTooltip(graphics, mouseX, mouseY);
        renderTemperatureTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

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
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, PANEL_TEXT_COLOR, false);
        graphics.drawString(this.font, formatTemperature(menu.getTemperature()),
                TEMPERATURE_X, TEMPERATURE_Y, PANEL_TEXT_COLOR, false);
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
        CryoCondenserBlockEntity condenser = menu.getBlockEntity();
        List<Component> lines = new ArrayList<>();
        List<Map.Entry<ResourceLocation, Double>> gases = condenser.getClientGasMasses().entrySet().stream()
                .filter(entry -> entry.getValue() > 0.001)
                .toList();
        if (gases.isEmpty()) {
            lines.add(Component.translatable("gui.zps.vaporizer.gas_empty"));
        } else {
            for (Map.Entry<ResourceLocation, Double> entry : gases) {
                lines.add(Component.translatable("gui.zps.vaporizer.gas_line",
                        gasName(entry.getKey()), formatMass(entry.getValue())));
            }
            lines.add(Component.translatable("gui.zps.vaporizer.gas_temperature",
                    formatTemperature(condenser.getTemperature())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.zps.vaporizer.gas_pressure",
                    GasGaugeBlockEntity.Mode.PRESSURE.format(condenser.getPressure())).withStyle(ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void renderTemperatureTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isHovering(TEMPERATURE_X - 2, TEMPERATURE_Y - 1, TEMPERATURE_HOVER_WIDTH, TEMPERATURE_HOVER_HEIGHT,
                mouseX, mouseY)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.zps.vaporizer.temperature", formatTemperature(menu.getTemperature())));
        lines.add(statusLine());
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private Component statusLine() {
        String target = formatTemperature(menu.getTargetTemperature());
        return switch (menu.getStatus()) {
            case IDLE -> Component.translatable("gui.zps.cryo_condenser.status.idle");
            case COOLING -> Component.translatable("gui.zps.cryo_condenser.status.cooling", target);
            case NO_POWER -> Component.translatable("gui.zps.cryo_condenser.status.no_power", target);
            case CONDENSING -> Component.translatable("gui.zps.cryo_condenser.status.condensing");
            case OUTPUT_FULL -> Component.translatable("gui.zps.cryo_condenser.status.output_full");
        };
    }

    private static Component gasName(ResourceLocation id) {
        GasType gas = GasTypeRegistry.INSTANCE.getGasType(id);
        return Component.literal(gas != null ? gas.getName() : id.toString());
    }

    /** How opaque to draw the gas: proportional to how full the buffer is against its ceiling. */
    private int getGasAlpha() {
        CryoCondenserBlockEntity condenser = menu.getBlockEntity();
        double ceiling = condenser.getMaxPressure();
        if (ceiling <= 0) {
            return 0;
        }
        double fraction = condenser.getPressure() / ceiling;
        if (fraction <= 0) {
            return 0;
        }
        return Mth.clamp((int) Math.ceil(fraction * GAS_FILL_MAX_ALPHA), 0x10, GAS_FILL_MAX_ALPHA);
    }

    private int getEnergyFill() {
        int maxEnergy = menu.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return Mth.clamp((menu.getEnergyStored() * ENERGY_BAR_HEIGHT) / maxEnergy, 0, ENERGY_BAR_HEIGHT);
    }

    private static String formatTemperature(double kelvin) {
        return GasGaugeBlockEntity.Mode.TEMPERATURE.format(kelvin);
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
