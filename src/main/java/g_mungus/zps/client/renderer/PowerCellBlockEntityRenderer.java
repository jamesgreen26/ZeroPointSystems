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
 * Draws the divider ring that marks the charge level. Only a structure's controller renders, using the ring
 * model sized for the structure's footprint (1x1, 2x2 or 3x3) at the shared fill height.
 */
public class PowerCellBlockEntityRenderer implements BlockEntityRenderer<PowerCellBlockEntity> {
    private static final ItemStack DIVIDER_MODEL_STACK = new ItemStack(ModItems.POWER_CELL.get());
    /** Ring models indexed by structure width; each is centred on the origin cell's centre. */
    private static final ModelResourceLocation[] DIVIDER_BER_MODELS = {
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/power_cell_divider")),
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "block/power_cell_divider_2x2")),
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "block/power_cell_divider_3x3")),
    };
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

        int modelIndex = Math.min(width, DIVIDER_BER_MODELS.length) - 1;
        BakedModel dividerModel = Minecraft.getInstance().getModelManager().getModel(DIVIDER_BER_MODELS[modelIndex]);
        Level level = blockEntity.getLevel();
        BlockPos origin = blockEntity.getBlockPos();

        int light = packedLight;
        if (level != null && (width > 1 || height > 1)) {
            // Light the ring from the middle of the layer it sits in rather than from the controller's corner.
            int ringLayer = (int) Math.floor(ringCentre);
            light = LevelRenderer.getLightColor(level, origin.offset(width / 2, ringLayer, width / 2));
        }

        // The ring models are centred on the origin cell; the wider ones extend to cover the footprint.
        float centreOffset = (width - 1) / 2.0f;
        poseStack.pushPose();
        poseStack.translate(0.5f + centreOffset, 0.5f + ringOffset, 0.5f + centreOffset);
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

    @Override
    public boolean shouldRenderOffScreen(PowerCellBlockEntity blockEntity) {
        return blockEntity.isController() && (blockEntity.getWidth() > 1 || blockEntity.getHeight() > 1);
    }
}
