package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ImpactPistonBlockEntity;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback renderer for the Impact Piston's rod when Flywheel's backend is unavailable (e.g. on a
 * ship or with the backend turned off). {@link ImpactPistonVisual} draws the rod whenever Flywheel
 * is active, so this BER only kicks in otherwise.
 *
 * <p>The raised rod is kept from being culled with its block by
 * {@link ImpactPistonBlockEntity#getRenderBoundingBox()}.
 */
public class ImpactPistonBlockEntityRenderer implements BlockEntityRenderer<ImpactPistonBlockEntity> {
    public static final ResourceLocation ROD_MODEL = ZPSMod.resource("block/impact_piston_rod");
    private static final ItemStack MODEL_STACK = new ItemStack(ModItems.IMPACT_PISTON.get());

    private final ItemRenderer itemRenderer;

    public ImpactPistonBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ImpactPistonBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // The Flywheel visual renders the rod when its backend is active; only draw the fallback otherwise.
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        BakedModel rodModel = Minecraft.getInstance().getModelManager().getModel(ROD_MODEL);
        float offset = blockEntity.getRodOffset(partialTick);

        poseStack.pushPose();
        poseStack.translate(0.0f, offset * ImpactPistonBlockEntity.ROD_TRAVEL, 0.0f);
        // ItemRenderer.render applies translate(-0.5, -0.5, -0.5); the model is already in block space.
        poseStack.translate(0.5f, 0.5f, 0.5f);
        itemRenderer.render(
                MODEL_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                rodModel
        );
        poseStack.popPose();
    }
}
