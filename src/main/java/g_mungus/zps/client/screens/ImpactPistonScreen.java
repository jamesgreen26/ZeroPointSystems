package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.ImpactPistonBlockEntity;
import g_mungus.zps.recipe.ImpactInput;
import g_mungus.zps.recipe.ModRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only readout for the Impact Piston. The machine has no inventory, so there is nothing to
 * hold a container menu; this reads the client-side block entity directly, like the Robotic Arm's
 * screen does.
 */
public class ImpactPistonScreen extends Screen {
    private static final Component TITLE = Component.translatable("block.zps.impact_piston");
    private static final Component ENERGY_LABEL = Component.literal("Energy");
    private static final Component TARGET_LABEL = Component.literal("Target");
    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 10;
    private static final int SECTION_GAP = 12;
    private static final int TITLE_TO_ENERGY_LABEL_GAP = 14;
    private static final int TOP_SECTION_Y_OFFSET = -50;

    private final BlockPos blockPos;

    public ImpactPistonScreen(BlockPos blockPos) {
        super(GameNarrator.NO_TITLE);
        this.blockPos = blockPos;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int topSectionY = this.height / 2 + TOP_SECTION_Y_OFFSET;
        int energyLabelY = topSectionY + TITLE_TO_ENERGY_LABEL_GAP;
        int barX = centerX - 100;
        int barY = energyLabelY + LABEL_TO_CONTROL_GAP;
        int barInnerPadding = 1;
        int barInnerWidth = CONTROL_WIDTH - (barInnerPadding * 2);
        int barInnerHeight = CONTROL_HEIGHT - (barInnerPadding * 2);
        int targetLabelY = barY + CONTROL_HEIGHT + SECTION_GAP;

        graphics.drawCenteredString(this.font, TITLE, centerX, topSectionY, 0xFFFFFF);
        graphics.drawString(this.font, ENERGY_LABEL, barX, energyLabelY, 0xA0A0A0);

        ImpactPistonBlockEntity be = getBlockEntity();
        int energyStored = be != null ? be.getEnergyStored() : 0;
        int maxEnergy = be != null ? be.getMaxEnergyStored() : ImpactPistonBlockEntity.ENERGY_CAPACITY;
        float fillRatio = maxEnergy > 0 ? (float) energyStored / (float) maxEnergy : 0.0F;
        int filledWidth = Mth.clamp(Math.round(fillRatio * barInnerWidth), 0, barInnerWidth);

        graphics.fill(barX, barY, barX + CONTROL_WIDTH, barY + CONTROL_HEIGHT, 0xFF111111);
        graphics.fill(barX + barInnerPadding, barY + barInnerPadding,
                barX + barInnerPadding + barInnerWidth, barY + barInnerPadding + barInnerHeight, 0xFF2A2A2A);
        graphics.fill(barX + barInnerPadding, barY + barInnerPadding,
                barX + barInnerPadding + filledWidth, barY + barInnerPadding + barInnerHeight, 0xFF194763);

        String energyText = energyStored + " / " + maxEnergy + " FE";
        graphics.drawCenteredString(this.font, energyText, centerX, barY + 6, 0xFFFFFF);

        graphics.drawString(this.font, TARGET_LABEL, barX, targetLabelY, 0xA0A0A0);
        graphics.drawString(this.font, describeTarget(), barX, targetLabelY + LABEL_TO_CONTROL_GAP, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Names the block under the piston and whether it has an impact recipe, using the synced recipe manager. */
    private Component describeTarget() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return Component.literal("-");
        }
        Level level = this.minecraft.level;
        BlockState below = level.getBlockState(this.blockPos.below());
        Component name = below.getBlock().getName();
        if (below.isAir()) {
            return Component.literal("Nothing below").withStyle(ChatFormatting.GRAY);
        }
        boolean hasRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipes.IMPACT_TYPE.get(), new ImpactInput(below), level)
                .isPresent();
        return hasRecipe
                ? name.copy().withStyle(ChatFormatting.AQUA)
                : name.copy().withStyle(ChatFormatting.GRAY).append(Component.literal(" (no recipe)").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ImpactPistonBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        if (!(this.minecraft.level.getBlockEntity(this.blockPos) instanceof ImpactPistonBlockEntity be)) return null;
        return be;
    }
}
