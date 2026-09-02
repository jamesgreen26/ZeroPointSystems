package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.networking.CreativeGasGeneratorSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
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
 * Settings for the Creative Gas Generator: which gas, how much per tick, and how hot.
 *
 * <p>A plain screen rather than a container screen — the block has no inventory, so there is
 * nothing for a menu to hold. Every control pushes the whole settings block to the server as it
 * moves, the way the robotic arm's screen does, so there is nothing to confirm on the way out.
 */
public class CreativeGasGeneratorScreen extends Screen {

    private static final Component TITLE = Component.translatable("block.zps.creative_gas_generator");
    private static final Component GAS_LABEL = Component.literal("Gas");
    private static final Component RATE_LABEL = Component.literal("Emission rate");
    private static final Component TEMPERATURE_LABEL = Component.literal("Emission temperature");

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int SECTION_GAP = 12;
    private static final int TITLE_TO_FIRST_LABEL_GAP = 20;
    /** Half the stack's height, so the three rows sit centred on the screen. */
    private static final int TOP_SECTION_Y_OFFSET = -70;

    private final BlockPos blockPos;

    /** Every gas anyone has registered, in a stable order so the button does not shuffle. */
    private final List<GasType> gases = new ArrayList<>();

    private int gasIndex;
    private RateSlider rateSlider;
    private TemperatureSlider temperatureSlider;

    // Laid out in init(), read back in render() so labels track their controls.
    private int titleY;
    private int gasLabelY;
    private int rateLabelY;
    private int temperatureLabelY;

    public CreativeGasGeneratorScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        gases.clear();
        gases.addAll(GasTypeRegistry.INSTANCE.getGAS_TYPES().values());
        gases.sort(Comparator.comparing(gas -> gas.getResourceLocation().toString()));

        CreativeGasGeneratorBlockEntity generator = getBlockEntity();
        ResourceLocation currentGas = generator != null ? generator.getGasId() : null;
        gasIndex = Math.max(0, indexOf(currentGas));
        double rate = generator != null ? generator.getRate() : 0.0;
        double temperature = generator != null ? generator.getEmissionTemperature() : 300.0;

        int left = this.width / 2 - CONTROL_WIDTH / 2;
        int centerY = this.height / 2;

        titleY = centerY + TOP_SECTION_Y_OFFSET;
        gasLabelY = titleY + TITLE_TO_FIRST_LABEL_GAP;
        int gasButtonY = gasLabelY + LABEL_TO_CONTROL_GAP;
        rateLabelY = gasButtonY + CONTROL_HEIGHT + SECTION_GAP;
        int rateSliderY = rateLabelY + LABEL_TO_CONTROL_GAP;
        temperatureLabelY = rateSliderY + CONTROL_HEIGHT + SECTION_GAP;
        int temperatureSliderY = temperatureLabelY + LABEL_TO_CONTROL_GAP;

        Button gasButton = this.addRenderableWidget(Button.builder(gasButtonText(), button -> {
                    if (gases.isEmpty()) {
                        return;
                    }
                    gasIndex = (gasIndex + 1) % gases.size();
                    button.setMessage(gasButtonText());
                    sendSettings();
                })
                .bounds(left, gasButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
        gasButton.active = gases.size() > 1;

        this.rateSlider = this.addRenderableWidget(
                new RateSlider(left, rateSliderY, CONTROL_WIDTH, CONTROL_HEIGHT, rate));
        this.temperatureSlider = this.addRenderableWidget(
                new TemperatureSlider(left, temperatureSliderY, CONTROL_WIDTH, CONTROL_HEIGHT, temperature));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - CONTROL_WIDTH / 2;

        graphics.drawCenteredString(this.font, TITLE, this.width / 2, titleY, 0xFFFFFF);
        graphics.drawString(this.font, GAS_LABEL, left, gasLabelY, 0xA0A0A0);
        graphics.drawString(this.font, RATE_LABEL, left, rateLabelY, 0xA0A0A0);
        graphics.drawString(this.font, TEMPERATURE_LABEL, left, temperatureLabelY, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendSettings() {
        ResourceLocation gas = selectedGas();
        if (gas == null) {
            return;
        }
        ZPSGamePackets.sendToServer(new CreativeGasGeneratorSettingsC2SPacket(
                this.blockPos, gas, this.rateSlider.getRate(),
                this.temperatureSlider.getTemperature()));
    }

    private @Nullable ResourceLocation selectedGas() {
        if (gases.isEmpty()) {
            CreativeGasGeneratorBlockEntity generator = getBlockEntity();
            return generator != null ? generator.getGasId() : null;
        }
        return gases.get(Math.min(gasIndex, gases.size() - 1)).getResourceLocation();
    }

    private int indexOf(@Nullable ResourceLocation gas) {
        if (gas == null) {
            return 0;
        }
        for (int index = 0; index < gases.size(); index++) {
            if (gases.get(index).getResourceLocation().equals(gas)) {
                return index;
            }
        }
        return 0;
    }

    private Component gasButtonText() {
        if (gases.isEmpty()) {
            return Component.literal("No gases registered");
        }
        return Component.literal(gases.get(Math.min(gasIndex, gases.size() - 1)).getName());
    }

    private @Nullable CreativeGasGeneratorBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos)
                instanceof CreativeGasGeneratorBlockEntity generator)) return null;
        return generator;
    }

    /**
     * Rate in kilograms per tick, and the off switch: slid to zero the block emits nothing.
     *
     * <p>Cubed, because everything interesting happens in the bottom hundredth of the range — a
     * linear slider would put every usable value in its first two pixels.
     */
    private class RateSlider extends AbstractSliderButton {
        private RateSlider(int x, int y, int width, int height, double initialRate) {
            super(x, y, width, height, CommonComponents.EMPTY, toSliderValue(initialRate));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("%.4f kg/t", getRate())));
        }

        @Override
        protected void applyValue() {
            sendSettings();
        }

        double getRate() {
            double rate = CreativeGasGeneratorBlockEntity.MAX_RATE * Math.pow(this.value, 3);
            // Snap to what the label shows, so what is sent is what is read.
            return Math.round(rate * 10000.0) / 10000.0;
        }

        private static double toSliderValue(double rate) {
            double clamped = Mth.clamp(rate, 0.0, CreativeGasGeneratorBlockEntity.MAX_RATE);
            return Math.cbrt(clamped / CreativeGasGeneratorBlockEntity.MAX_RATE);
        }
    }

    /** Emission temperature in Kelvin, linear across the block's whole range. */
    private class TemperatureSlider extends AbstractSliderButton {
        private TemperatureSlider(int x, int y, int width, int height, double initialTemperature) {
            super(x, y, width, height, CommonComponents.EMPTY, toSliderValue(initialTemperature));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(Math.round(getTemperature()) + " K"));
        }

        @Override
        protected void applyValue() {
            sendSettings();
        }

        double getTemperature() {
            double range = CreativeGasGeneratorBlockEntity.MAX_TEMPERATURE
                    - CreativeGasGeneratorBlockEntity.MIN_TEMPERATURE;
            return Math.round(CreativeGasGeneratorBlockEntity.MIN_TEMPERATURE + this.value * range);
        }

        private static double toSliderValue(double temperature) {
            double range = CreativeGasGeneratorBlockEntity.MAX_TEMPERATURE
                    - CreativeGasGeneratorBlockEntity.MIN_TEMPERATURE;
            double clamped = Mth.clamp(temperature, CreativeGasGeneratorBlockEntity.MIN_TEMPERATURE,
                    CreativeGasGeneratorBlockEntity.MAX_TEMPERATURE);
            return (clamped - CreativeGasGeneratorBlockEntity.MIN_TEMPERATURE) / range;
        }
    }
}
