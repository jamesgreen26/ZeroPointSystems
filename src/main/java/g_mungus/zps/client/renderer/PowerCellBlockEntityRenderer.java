package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the divider ring that marks the charge level. Only a structure's controller renders, and it draws one
 * ring per column of the structure at the shared fill height, so a multiblock reads as a single large cell.
 */
public class PowerCellBlockEntityRenderer implements BlockEntityRenderer<PowerCellBlockEntity> {
    private static final ItemStack DIVIDER_MODEL_STACK = new ItemStack(ModItems.POWER_CELL.get());
    private static final ModelResourceLocation DIVIDER_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/power_cell_divider"));
    private final ItemRenderer itemRenderer;

    public PowerCellBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PowerCellBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!blockEntity.isController()) {
            return;
        }
        int maxEnergy = blockEntity.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return;
        }

        float fill = (float) blockEntity.getEnergyStored() / maxEnergy;
        float previousSmoothedFill = blockEntity.getClientSmoothedFill();
        float smoothing = 0.2f;
        float smoothedFill = previousSmoothedFill + ((fill - previousSmoothedFill) * smoothing);
        if (Math.abs(smoothedFill - fill) < 0.0005f) {
            smoothedFill = fill;
        }
        blockEntity.setClientSmoothedFill(smoothedFill);

        int width = blockEntity.getWidth();
        int height = blockEntity.getHeight();
        // The ring model sits at y = 2..5 px of a block centred on the origin; lift it to the fill height.
        float ringCentre = PowerCellBlockEntity.ringCentrePx(smoothedFill, height) / 16.0f;
        float ringOffset = ringCentre - 3.5f / 16.0f;

        BakedModel dividerModel = Minecraft.getInstance().getModelManager().getModel(DIVIDER_BER_MODEL);
        Level level = blockEntity.getLevel();
        BlockPos origin = blockEntity.getBlockPos();
        int ringLayer = (int) Math.floor(ringCentre);

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                int light = packedLight;
                if (level != null && (width > 1 || height > 1)) {
                    light = LevelRenderer.getLightColor(level, origin.offset(xOffset, ringLayer, zOffset));
                }
                poseStack.pushPose();
                poseStack.translate(0.5f + xOffset, 0.5f + ringOffset, 0.5f + zOffset);
                itemRenderer.render(
                        DIVIDER_MODEL_STACK,
                        ItemDisplayContext.NONE,
                        false,
                        poseStack,
                        bufferSource,
                        light,
                        packedOverlay,
                        dividerModel
                );
                poseStack.popPose();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PowerCellBlockEntity blockEntity) {
        return blockEntity.isController() && (blockEntity.getWidth() > 1 || blockEntity.getHeight() > 1);
    }
}
