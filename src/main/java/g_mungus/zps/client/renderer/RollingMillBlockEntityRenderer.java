package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.RollingMillBlock;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import g_mungus.zps.blockentity.RollingMillBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback renderer for the spinning rollers when Flywheel's backend is unavailable (e.g. on a ship or
 * with the backend turned off). {@link RollingMillVisual} draws the rollers whenever Flywheel is active,
 * so this BER only kicks in otherwise. The disc layout and spin mirror the visual exactly.
 */
public class RollingMillBlockEntityRenderer implements BlockEntityRenderer<RollingMillBlockEntity> {
    public static final ModelResourceLocation ROLLER_MODEL =
            ModelResourceLocation.standalone(ZPSMod.resource("block/rolling_mill_roller"));
    private static final ItemStack MODEL_STACK = new ItemStack(ModItems.ROLLING_MILL.get());

    private final ItemRenderer itemRenderer;

    public RollingMillBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(RollingMillBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // The Flywheel visual renders the rollers when its backend is active; only draw the fallback otherwise.
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        BakedModel rollerModel = Minecraft.getInstance().getModelManager().getModel(ROLLER_MODEL);
        float angle = blockEntity.advanceRenderSpin(partialTick);
        // Rotate the whole layout 90 deg when the mill faces along X so the discs track the block's facing.
        float facingYaw = blockEntity.getBlockState().getValue(RollingMillBlock.FACING).getAxis() == Direction.Axis.X ? 90.0f : 0.0f;

        renderDisc(rollerModel, poseStack, bufferSource, packedLight, packedOverlay, 0.25f, 0.5f, 0.5f, facingYaw, angle);
        renderDisc(rollerModel, poseStack, bufferSource, packedLight, packedOverlay, 0.75f, 0.5f, 0.5f, facingYaw, -angle);
    }

    private void renderDisc(BakedModel model, PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedLight, int packedOverlay, float cx, float cy, float cz, float facingYaw, float angle) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);      // to block centre
        poseStack.mulPose(Axis.YP.rotationDegrees(facingYaw)); // orient the side positions to facing
        poseStack.translate(-0.5f, -0.5f, -0.5f);   // back to unrotated block corner
        poseStack.translate(cx, cy, cz);            // move disc centre to its staggered side position
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));     // spin about the vertical disc axis
        // ItemRenderer.render applies translate(-0.5, -0.5, -0.5); the OBJ is centered at x/z 0, y 0.5.
        poseStack.translate(0.5f, 0.0f, 0.5f);
        itemRenderer.render(
                MODEL_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                model
        );
        poseStack.popPose();
    }
}
