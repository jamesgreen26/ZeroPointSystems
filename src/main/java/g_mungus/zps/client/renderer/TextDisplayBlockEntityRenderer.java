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

import java.util.ArrayList;
import java.util.List;

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

        // Row height in font pixels. Characters advance horizontally by their
        // natural glyph width, but rows keep this fixed height so that multi-block
        // displays still tile vertically.
        int cellSize = 8;

        // Top-left origin of the grid, i.e. the pixel position of logical column 0.
        // It is identical across the pieces of a multi-block display (which are
        // physically offset by one block / 128 font pixels), so laying glyphs out in
        // pixels from this origin keeps them aligned across blocks.
        int originX = (layout.textOffset.x() * cellSize) - 64 - minDisplayableCol * cellSize;
        int originY = (layout.textOffset.y() * cellSize) - 64 - minDisplayableRow * cellSize;

        // Word-aware line breaking. Every piece of a multi-block display sees the
        // full text and the same budget, so they all compute identical breaks and
        // positions. Each glyph advances by its natural width, and a line wraps once
        // its real pixel width would overrun the display.
        int lineBudget = maxCols * cellSize;
        List<Glyph> glyphs = layoutGlyphs(text, lineBudget, maxRows);

        // The window (in pixels from logical column 0) that belongs to this block.
        int minDisplayableX = minDisplayableCol * cellSize;
        int maxDisplayableX = maxDisplayableCol * cellSize;

        for (Glyph glyph : glyphs) {
            boolean isWithinDisplayableBounds =
                    glyph.x() >= minDisplayableX && glyph.x() < maxDisplayableX &&
                    glyph.row() >= minDisplayableRow && glyph.row() < maxDisplayableRow;

            if (!isWithinDisplayableBounds) {
                continue;
            }

            this.font.drawInBatch(
                    String.valueOf(glyph.c()),
                    originX + glyph.x(),
                    originY + glyph.row() * cellSize,
                    0xFFFFFF,
                    false,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    packedLight
            );
        }

        poseStack.popPose();
    }

    /** A single positioned character. {@code x} is font pixels from logical column 0. */
    private record Glyph(char c, int row, float x) {}

    /**
     * Lays the text out into positioned glyphs with word-aware line breaking.
     * Words move to the next line as a unit when they no longer fit; a word wider
     * than a whole line is hard-broken across lines. Explicit newlines start a new
     * row. Glyphs on rows past {@code maxRows} are dropped.
     */
    private List<Glyph> layoutGlyphs(String text, int lineBudget, int maxRows) {
        List<Glyph> glyphs = new ArrayList<>();
        int row = 0;
        float lineWidth = 0f;
        int i = 0;
        int length = text.length();

        while (i < length && row < maxRows) {
            char c = text.charAt(i);

            if (c == '\n') {
                row++;
                lineWidth = 0f;
                i++;
                continue;
            }

            if (c == ' ') {
                glyphs.add(new Glyph(c, row, lineWidth));
                lineWidth += advance(c);
                i++;
                continue;
            }

            // Measure the whole word ahead so it can wrap as a unit.
            int end = i;
            float wordWidth = 0f;
            while (end < length && text.charAt(end) != ' ' && text.charAt(end) != '\n') {
                wordWidth += advance(text.charAt(end));
                end++;
            }

            boolean wordFitsOnOneLine = wordWidth <= lineBudget;
            if (lineWidth > 0f && wordFitsOnOneLine && lineWidth + wordWidth > lineBudget) {
                row++;
                lineWidth = 0f;
            }

            for (int k = i; k < end && row < maxRows; k++) {
                char wc = text.charAt(k);
                float charWidth = advance(wc);

                // A word too wide for any line is hard-broken as we go.
                if (!wordFitsOnOneLine && lineWidth > 0f && lineWidth + charWidth > lineBudget) {
                    row++;
                    lineWidth = 0f;
                    if (row >= maxRows) {
                        break;
                    }
                }

                glyphs.add(new Glyph(wc, row, lineWidth));
                lineWidth += charWidth;
            }
            i = end;
        }

        return glyphs;
    }

    private float advance(char c) {
        return this.font.width(String.valueOf(c));
    }

}
