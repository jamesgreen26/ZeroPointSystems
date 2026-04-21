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
            int rgb = decodeRgb565(encodedPixel);
            addPixelQuad(consumer, pose, x, y, cellSize, rgb);
        }

        poseStack.popPose();
    }

    // The encoded frame uses one UTF-16 code unit per pixel, packed as RGB565.
    private static int decodeRgb565(char encodedPixel) {
        int value = encodedPixel;
        int red = ((value >> 11) & 0x1F) * 255 / 31;
        int green = ((value >> 5) & 0x3F) * 255 / 63;
        int blue = (value & 0x1F) * 255 / 31;
        return (red << 16) | (green << 8) | blue;
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
