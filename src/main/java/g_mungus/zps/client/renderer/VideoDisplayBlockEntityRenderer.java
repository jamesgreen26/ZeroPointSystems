package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayLayout;
import g_mungus.zps.block.cableNetwork.light_pipe.VideoDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.VideoDisplayBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class VideoDisplayBlockEntityRenderer implements BlockEntityRenderer<VideoDisplayBlockEntity> {

    public VideoDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            VideoDisplayBlockEntity blockEntity,
            float partialTick,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(VideoDisplayBlock.FACING);
        DisplayLayout layout = blockState.getValue(VideoDisplayBlock.LAYOUT);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> {}
        }

        poseStack.translate(0, 0, 0.501);

        float scale = 1.0f / 128.0f;
        poseStack.scale(scale, -scale, scale);

        String encodedFrame = blockEntity.getDisplayText();
        int maxCols = layout.videoResolution.x() * layout.blockDimensions.x();
        int maxRows = layout.videoResolution.y() * layout.blockDimensions.y();

        int minDisplayableCol = layout.videoOffset.x() == 0 ? layout.videoResolution.x() : 0;
        int minDisplayableRow = layout.videoOffset.y() == 0 ? layout.videoResolution.y() : 0;
        int maxDisplayableCol = layout.videoOffset.x() == 0 ? maxCols : layout.videoResolution.x();
        int maxDisplayableRow = layout.videoOffset.y() == 0 ? maxRows : layout.videoResolution.y();

        float cellSize = 8.0f;
        float originX = (layout.videoOffset.x() * cellSize) - 64.0f - minDisplayableCol * cellSize;
        float originY = (layout.videoOffset.y() * cellSize) - 64.0f - minDisplayableRow * cellSize;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();

        int pixelIndex = 0;
        int maxPixels = maxCols * maxRows;
        for (int i = 0; i < encodedFrame.length() && pixelIndex < maxPixels; i++) {
            char encodedPixel = encodedFrame.charAt(i);
            if (encodedPixel == '\n' || encodedPixel == '\r') {
                continue;
            }

            int col = pixelIndex % maxCols;
            int row = pixelIndex / maxCols;
            pixelIndex++;

            if (row >= maxRows) {
                break;
            }

            boolean isWithinDisplayableBounds =
                    col >= minDisplayableCol && col < maxDisplayableCol &&
                    row >= minDisplayableRow && row < maxDisplayableRow;
            if (!isWithinDisplayableBounds) {
                continue;
            }

            float x = originX + col * cellSize;
            float y = originY + row * cellSize;
            int rgb = decodeWarmthColor(encodedPixel);
            addPixelQuad(consumer, pose, x, y, cellSize, rgb);
        }

        poseStack.popPose();
    }

    // Converts a codepoint into a 64-entry looping warmth palette using (code - 33) mod 64.
    // Low values are blue, middle values are green-yellow, high values are red.
    private static int decodeWarmthColor(char encodedPixel) {
        int value = Math.floorMod(encodedPixel - 33, 64);
        float t = value / 63.0f;

        int rgb;
        if (t <= 0.5f) {
            rgb = lerpColor(0x0000FF, 0xB6FF00, t * 2.0f);
        } else {
            rgb = lerpColor(0xB6FF00, 0xFF0000, (t - 0.5f) * 2.0f);
        }

        // Apply a subtle saturation dip across the yellow-orange region.
        float band = smoothStep(0.42f, 0.62f, t) - smoothStep(0.72f, 0.90f, t);
        float desaturation = 0.16f * Math.max(0.0f, band);
        return desaturate(rgb, desaturation);
    }

    private static int lerpColor(int from, int to, float t) {
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;

        int r = Math.round(r0 + (r1 - r0) * t);
        int g = Math.round(g0 + (g1 - g0) * t);
        int b = Math.round(b0 + (b1 - b0) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float t = (x - edge0) / (edge1 - edge0);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private static int desaturate(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float gray = 0.299f * r + 0.587f * g + 0.114f * b;
        int outR = Math.round(r + (gray - r) * amount);
        int outG = Math.round(g + (gray - g) * amount);
        int outB = Math.round(b + (gray - b) * amount);
        return (outR << 16) | (outG << 8) | outB;
    }

    private static void addPixelQuad(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float cellSize, int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        float x2 = x + cellSize;
        float y2 = y + cellSize;

        consumer.vertex(pose.pose(), x, y, 0.0f).color(red, green, blue, 255).endVertex();
        consumer.vertex(pose.pose(), x, y2, 0.0f).color(red, green, blue, 255).endVertex();
        consumer.vertex(pose.pose(), x2, y2, 0.0f).color(red, green, blue, 255).endVertex();
        consumer.vertex(pose.pose(), x2, y, 0.0f).color(red, green, blue, 255).endVertex();
    }
}
