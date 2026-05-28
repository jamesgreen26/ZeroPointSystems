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
import org.jetbrains.annotations.NotNull;

public class RoboticArmScreen extends Screen {
    private static final Component TITLE = Component.literal("Robotic Arm");
    private static final Component RETRIEVE_LABEL = Component.literal("\"take_items\" stack size");
    private static final Component VIEW_RANGE_LABEL = Component.literal("View Range");
    private static final Component ENERGY_LABEL = Component.literal("Energy");

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
        int sliderY = centerY - 10;
        int viewRangeButtonY = centerY + 30;
        int bottomButtonsY = centerY + 60;

        this.retrieveSlider = this.addRenderableWidget(new RetrieveAmountSlider(centerX - 100, sliderY, 200, 20, retrieveAmount));
        this.viewRangeButton = this.addRenderableWidget(Button.builder(viewRangeButtonText(), button -> {
                    this.viewRange = !this.viewRange;
                    button.setMessage(viewRangeButtonText());
                })
                .bounds(centerX - 100, viewRangeButtonY, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
                    sendSettings();
                    this.onClose();
                })
                .bounds(centerX - 100, bottomButtonsY, 98, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(centerX + 2, bottomButtonsY, 98, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, TITLE, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        graphics.drawString(this.font, ENERGY_LABEL, this.width / 2 - 100, this.height / 2 - 42, 0xA0A0A0);
        RoboticArmBlockEntity be = getBlockEntity();
        int energyStored = be != null ? be.getEnergyStored() : 0;
        int maxEnergy = be != null ? be.getMaxEnergyStored() : RoboticArmBlockEntity.ENERGY_CAPACITY;
        graphics.drawString(this.font, energyStored + " / " + maxEnergy + " FE", this.width / 2 - 52, this.height / 2 - 42, 0x55FF55);
        graphics.drawString(this.font, RETRIEVE_LABEL, this.width / 2 - 100, this.height / 2 - 22, 0xA0A0A0);
        graphics.drawString(this.font, VIEW_RANGE_LABEL, this.width / 2 - 100, this.height / 2 + 18, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendSettings() {
        ZPSGamePackets.INSTANCE.sendToServer(new RoboticArmSettingsC2SPacket(this.blockPos, this.retrieveSlider.getAmount(), this.viewRange));
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
            // Sent on Done.
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
