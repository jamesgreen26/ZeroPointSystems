package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.networking.RoboticArmSettingsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class RoboticArmScreen extends Screen {
    private static final Component TITLE = Component.literal("Robotic Arm");
    private static final Component ENERGY_LABEL = Component.literal("Energy");
    private static final Component RETRIEVE_LABEL = Component.literal("Item transfer stack size");
    private static final Component VIEW_RANGE_LABEL = Component.literal("View Range");
    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int SECTION_GAP = 12;
    private static final int TITLE_TO_ENERGY_LABEL_GAP = 14;
    private static final int TOP_SECTION_Y_OFFSET = -76;

    private final BlockPos blockPos;
    private RetrieveAmountSlider retrieveSlider;
    private Button viewRangeButton;
    private boolean viewRange;

    public RoboticArmScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        RoboticArmBlockEntity be = getBlockEntity();
        int retrieveAmount = be != null ? be.getRetrieveAmount() : 1;
        this.viewRange = be != null && be.isViewRange();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int topSectionY = centerY + TOP_SECTION_Y_OFFSET;
        int energyLabelY = topSectionY + TITLE_TO_ENERGY_LABEL_GAP;
        int barY = energyLabelY + LABEL_TO_CONTROL_GAP;
        int retrieveLabelY = barY + CONTROL_HEIGHT + SECTION_GAP;
        int sliderY = retrieveLabelY + LABEL_TO_CONTROL_GAP;
        int viewRangeLabelY = sliderY + CONTROL_HEIGHT + SECTION_GAP;
        int viewRangeButtonY = viewRangeLabelY + LABEL_TO_CONTROL_GAP;

        this.retrieveSlider = this.addRenderableWidget(new RetrieveAmountSlider(centerX - 100, sliderY, CONTROL_WIDTH, CONTROL_HEIGHT, retrieveAmount));
        this.viewRangeButton = this.addRenderableWidget(Button.builder(viewRangeButtonText(), button -> {
                    this.viewRange = !this.viewRange;
                    button.setMessage(viewRangeButtonText());
                    sendSettings();
                })
                .bounds(centerX - 100, viewRangeButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int topSectionY = centerY + TOP_SECTION_Y_OFFSET;
        int energyLabelY = topSectionY + TITLE_TO_ENERGY_LABEL_GAP;
        int barX = centerX - 100;
        int barY = energyLabelY + LABEL_TO_CONTROL_GAP;
        int barWidth = CONTROL_WIDTH;
        int barHeight = CONTROL_HEIGHT;
        int barInnerPadding = 1;
        int barInnerWidth = barWidth - (barInnerPadding * 2);
        int barInnerHeight = barHeight - (barInnerPadding * 2);
        int retrieveLabelY = barY + barHeight + SECTION_GAP;
        int viewRangeLabelY = retrieveLabelY + CONTROL_HEIGHT + SECTION_GAP + LABEL_TO_CONTROL_GAP;

        graphics.drawCenteredString(this.font, TITLE, centerX, topSectionY, 0xFFFFFF);
        graphics.drawString(this.font, ENERGY_LABEL, centerX - 100, energyLabelY, 0xA0A0A0);
        RoboticArmBlockEntity be = getBlockEntity();
        int energyStored = be != null ? be.getEnergyStored() : 0;
        int maxEnergy = be != null ? be.getMaxEnergyStored() : RoboticArmBlockEntity.ENERGY_CAPACITY;
        float fillRatio = maxEnergy > 0 ? (float) energyStored / (float) maxEnergy : 0.0F;
        int filledWidth = Mth.clamp(Math.round(fillRatio * barInnerWidth), 0, barInnerWidth);

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF111111);
        graphics.fill(barX + barInnerPadding, barY + barInnerPadding, barX + barInnerPadding + barInnerWidth, barY + barInnerPadding + barInnerHeight, 0xFF2A2A2A);
        graphics.fill(barX + barInnerPadding, barY + barInnerPadding, barX + barInnerPadding + filledWidth, barY + barInnerPadding + barInnerHeight, 0xFF194763);

        String energyText = energyStored + " / " + maxEnergy + " FE";
        graphics.drawCenteredString(this.font, energyText, centerX, barY + 6, 0xFFFFFF);
        graphics.drawString(this.font, RETRIEVE_LABEL, centerX - 100, retrieveLabelY, 0xA0A0A0);
        graphics.drawString(this.font, VIEW_RANGE_LABEL, centerX - 100, viewRangeLabelY, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendSettings() {
        ZPSGamePackets.sendToServer(new RoboticArmSettingsC2SPacket(this.blockPos, this.retrieveSlider.getAmount(), this.viewRange));
    }

    private Component viewRangeButtonText() {
        return this.viewRange ? Component.literal("On") : Component.literal("Off");
    }

    private RoboticArmBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof RoboticArmBlockEntity be)) return null;
        return be;
    }

    private class RetrieveAmountSlider extends AbstractSliderButton {
        protected RetrieveAmountSlider(int x, int y, int width, int height, int initialAmount) {
            super(x, y, width, height, CommonComponents.EMPTY, toSliderValue(initialAmount));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(Integer.toString(getAmount())));
        }

        @Override
        protected void applyValue() {
            sendSettings();
        }

        int getAmount() {
            int range = RoboticArmBlockEntity.MAX_RETRIEVE_AMOUNT - RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT;
            return RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT + (int) Math.round(this.value * range);
        }

        private static double toSliderValue(int amount) {
            int clamped = Math.max(RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT, Math.min(RoboticArmBlockEntity.MAX_RETRIEVE_AMOUNT, amount));
            int range = RoboticArmBlockEntity.MAX_RETRIEVE_AMOUNT - RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT;
            if (range <= 0) return 0.0D;
            return (double) (clamped - RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT) / (double) range;
        }
    }
}
