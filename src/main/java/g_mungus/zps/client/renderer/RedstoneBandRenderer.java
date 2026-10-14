package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.blockentity.RedstoneBandSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback for {@link RedstoneBandVisual} when Flywheel's backend is unavailable, on a
 * ship or with the backend turned off: the same tinted band, drawn through the vanilla buffer.
 */
public class RedstoneBandRenderer<T extends BlockEntity & RedstoneBandSource> implements BlockEntityRenderer<T> {

    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(RedstoneBand.TEXTURE);

    public RedstoneBandRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())
                || !RedstoneBand.isVisible(state)) {
            return;
        }

        int rgb = RedstoneBand.tintRgb(blockEntity.getBandLevel());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(RedstoneBand.rotationFor(state.getValue(TransformerBlock.FACING)));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        for (float[] quad : RedstoneBand.quadsFor(blockEntity)) {
            for (int at = 0; at < 20; at += 5) {
                consumer.addVertex(pose, quad[at], quad[at + 1], quad[at + 2])
                        .setColor(r, g, b, 255)
                        .setUv(quad[at + 3], quad[at + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        // A lamp: lit by its tint, not by the world.
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, quad[20], quad[21], quad[22]);
            }
        }

        poseStack.popPose();
    }
}
