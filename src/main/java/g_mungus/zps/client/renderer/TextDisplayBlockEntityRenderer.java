package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayLayout;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TextDisplayBlockEntityRenderer implements BlockEntityRenderer<TextDisplayBlockEntity> {

    final Font font;

    public TextDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }
    @Override
    public void render(
            TextDisplayBlockEntity blockEntity,
            float partialTick,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(TextDisplayBlock.FACING);
        DisplayLayout layout = blockState.getValue(TextDisplayBlock.LAYOUT);

        poseStack.pushPose();

        // Center on block
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate to face direction
        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> {}
        }

        // Move slightly off the face to prevent z-fighting
        poseStack.translate(0, 0, 0.501);

        /*
         * Font units:
         * 1 font pixel = 1 unit
         * We want 128×128 font pixels across the block face
         */
        float scale = 1.0f / 128.0f;
        poseStack.scale(scale, -scale, scale);

        String text = blockEntity.getDisplayText();
        int maxCols = layout.textResolution.x() * layout.blockDimensions.x();
        int maxRows = layout.textResolution.y() * layout.blockDimensions.y();

        int minDisplayableCol = layout.textOffset.x() == 0 ? layout.textResolution.x() : 0;
        int minDisplayableRow = layout.textOffset.y() == 0 ? layout.textResolution.y() : 0;
        int maxDisplayableCol = layout.textOffset.x() == 0 ? maxCols : layout.textResolution.x();
        int maxDisplayableRow = layout.textOffset.y() == 0 ? maxRows : layout.textResolution.y();

        int cellSize = 8; // font pixels per character cell

        // Top-left origin of the grid
        int originX = (layout.textOffset.x() * cellSize) - 64 - minDisplayableCol * cellSize;
        int originY = (layout.textOffset.y() * cellSize) - 64 - minDisplayableRow * cellSize;

        int col = 0;
        int row = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Handle newline character
            if (c == '\n') {
                row++;
                col = 0;
                continue;
            }

            // Stop if we've exceeded the grid bounds
            if (row >= maxRows) {
                break;
            }
            boolean isWithinDisplayableBounds =
                    col >= minDisplayableCol && col < maxDisplayableCol &&
                    row >= minDisplayableRow && row < maxDisplayableRow;


            if (isWithinDisplayableBounds) {

                int x = originX + col * cellSize;
                int y = originY + row * cellSize;

                this.font.drawInBatch(
                        String.valueOf(c),
                        x,
                        y,
                        0xFFFFFF,
                        false,
                        poseStack.last().pose(),
                        bufferSource,
                        Font.DisplayMode.POLYGON_OFFSET,
                        0,
                        packedLight
                );
            }

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }

        poseStack.popPose();
    }

}
