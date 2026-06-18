package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

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

        float dividerYOffset = smoothedFill * (9.0f / 16.0f);

        BakedModel dividerModel = Minecraft.getInstance().getModelManager().getModel(DIVIDER_BER_MODEL);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f + dividerYOffset, 0.5f);
        itemRenderer.render(
                DIVIDER_MODEL_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                dividerModel
        );
        poseStack.popPose();
    }
}
