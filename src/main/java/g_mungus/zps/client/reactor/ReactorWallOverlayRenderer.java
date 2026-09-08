package g_mungus.zps.client.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import g_mungus.zps.block.reactor.ReactorPortBlock;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Fallback for {@link ReactorWallOverlayVisual} when Flywheel's backend is unavailable, on a ship
 * or with the backend turned off: the same tinted overlay on the block's outer face, drawn through
 * the vanilla buffer instead. The visual draws it whenever Flywheel is active, so this only kicks
 * in otherwise.
 */
public class ReactorWallOverlayRenderer implements BlockEntityRenderer<ReactorPortBlockEntity> {

    /**
     * How far in front of the face the quad sits. The visual leans on polygon offset for this;
     * the vanilla path has no such thing, so a sliver of real distance beats the face instead.
     */
    private static final float LIFT = 1f / 512f;

    /** One render type per mode, each on that mode's overlay texture. */
    private final Map<ReactorPortMode, RenderType> renderTypes = new EnumMap<>(ReactorPortMode.class);

    public ReactorWallOverlayRenderer(BlockEntityRendererProvider.Context context) {
        for (ReactorPortMode mode : ReactorPortMode.values()) {
            renderTypes.put(mode, RenderType.entityCutoutNoCull(ReactorWallOverlays.textureFor(mode)));
        }
    }

    @Override
    public void render(ReactorPortBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        int rgb = ReactorWallOverlays.tintRgb(blockEntity.getRedstoneLevel());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(ReactorWallOverlays.rotationFor(ReactorPortBlock.facing(blockEntity.getBlockState())));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        // The same quad as the Flywheel mesh: the north face, u east to west, v top to bottom.
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(renderTypes.get(blockEntity.getMode()));
        vertex(consumer, pose, 1f, 1f, 0f, 0f, r, g, b);
        vertex(consumer, pose, 1f, 0f, 0f, 1f, r, g, b);
        vertex(consumer, pose, 0f, 0f, 1f, 1f, r, g, b);
        vertex(consumer, pose, 0f, 1f, 1f, 0f, r, g, b);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float u, float v, int r, int g, int b) {
        consumer.addVertex(pose, x, y, -LIFT)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                // A lamp: lit by its tint, not by the world.
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, -1f);
    }
}
