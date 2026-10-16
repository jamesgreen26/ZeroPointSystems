package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayLayout;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import g_mungus.zps.client.renderer.text.TextVisualizers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class TextDisplayBlockEntityRenderer implements BlockEntityRenderer<TextDisplayBlockEntity> {

    /** A pixel is as wide as a row is high, so pixels tile as squares. */
    private static final int PIXEL_SIZE = 8;

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
        int width = layout.width;
        int height = layout.height;
        int column = layout.column;
        int row = layout.row;

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

        // Row height in font pixels. Characters advance horizontally by their
        // natural glyph width, but rows keep this fixed height so that multi-block
        // displays still tile vertically.
        int cellSize = 8;
        int blockSize = 128;
        // Blank border around the whole display, inside the bezel.
        int margin = 2 * cellSize;

        int maxCols = (width * blockSize - 2 * margin) / cellSize;
        int maxRows = (height * blockSize - 2 * margin) / cellSize;

        // The window (in pixels from logical column/row 0) that belongs to this block.
        int minDisplayableX = column * blockSize - margin;
        int maxDisplayableX = minDisplayableX + blockSize;
        int minDisplayableY = row * blockSize - margin;
        int maxDisplayableY = minDisplayableY + blockSize;

        // Top-left origin of the grid, i.e. the pixel position of logical column 0.
        // It is identical across the pieces of a multi-block display (which are
        // physically offset by one block / 128 font pixels), so laying glyphs out in
        // pixels from this origin keeps them aligned across blocks.
        int originX = -blockSize / 2 - minDisplayableX;
        int originY = -blockSize / 2 - minDisplayableY;

        // Large text draws every glyph at this many font pixels per font unit. The
        // per-block windows and grid origin stay in 1x pixels; only the glyph layout
        // works in scaled units. Block boundaries sit on multiples of 16 pixels, so
        // scaled rows never straddle one.
        int textScale = blockEntity.getTextScale();

        // Word-aware line breaking. Every piece of a multi-block display sees the
        // full text and the same budget, so they all compute identical breaks and
        // positions. Each glyph advances by its natural width, and a line wraps once
        // its real pixel width would overrun the display.
        int lineBudget = maxCols * cellSize / textScale;
        // Tags pick the visualizer for each part of the text, which picks each char's glyph and colour.
        List<TextVisualizers.VisualChar> chars = TextVisualizers.visualize(text, blockEntity.getTextColor());
        List<Glyph> glyphs = layoutGlyphs(chars, lineBudget, maxRows / textScale);

        // Glowing text ignores the light level, as on a sign touched with a glow ink sac.
        int textLight = blockEntity.isGlowingText() ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.scale(textScale, textScale, 1);

        for (Glyph glyph : glyphs) {
            float pixelX = glyph.x() * textScale;
            int pixelY = glyph.row() * cellSize * textScale;
            boolean isWithinDisplayableBounds =
                    pixelX >= minDisplayableX && pixelX < maxDisplayableX &&
                    pixelY >= minDisplayableY && pixelY < maxDisplayableY;

            if (!isWithinDisplayableBounds) {
                continue;
            }

            float x = (float) originX / textScale + glyph.x();
            float y = (float) originY / textScale + glyph.row() * cellSize;
            if (glyph.c().pixel()) {
                drawPixel(poseStack, bufferSource, x, y, PIXEL_SIZE, glyph.c().color(), textLight);
                continue;
            }

            this.font.drawInBatch(
                    String.valueOf(glyph.c().glyph()),
                    x,
                    y,
                    glyph.c().color(),
                    false,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    textLight
            );
        }

        poseStack.popPose();
    }

    /**
     * A filled square, lit like the text: one texture pixel of the block at default size. The
     * corners go in the order the font gives a glyph's, so it faces the same way.
     */
    private static void drawPixel(PoseStack poseStack, MultiBufferSource bufferSource, float x, float y, float size,
                                  int color, int light) {
        Matrix4f pose = poseStack.last().pose();
        int argb = 0xFF000000 | color;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.textBackground());
        buffer.vertex(pose, x, y, 0).color(argb).uv2(light).endVertex();
        buffer.vertex(pose, x, y + size, 0).color(argb).uv2(light).endVertex();
        buffer.vertex(pose, x + size, y + size, 0).color(argb).uv2(light).endVertex();
        buffer.vertex(pose, x + size, y, 0).color(argb).uv2(light).endVertex();
    }

    /** A single positioned character. {@code x} is font pixels from logical column 0. */
    private record Glyph(TextVisualizers.VisualChar c, int row, float x) {}

    /**
     * Lays the text out into positioned glyphs with word-aware line breaking.
     * Words move to the next line as a unit when they no longer fit; a word wider
     * than a whole line is hard-broken across lines. Explicit newlines start a new
     * row. Glyphs on rows past {@code maxRows} are dropped. Spaces and newlines are
     * read from the text as written; widths come from the glyphs drawn.
     */
    private List<Glyph> layoutGlyphs(List<TextVisualizers.VisualChar> text, int lineBudget, int maxRows) {
        List<Glyph> glyphs = new ArrayList<>();
        int row = 0;
        float lineWidth = 0f;
        int i = 0;
        int length = text.size();

        while (i < length && row < maxRows) {
            TextVisualizers.VisualChar c = text.get(i);

            if (c.source() == '\n') {
                row++;
                lineWidth = 0f;
                i++;
                continue;
            }

            if (c.source() == ' ' && !c.pixel()) {
                glyphs.add(new Glyph(c, row, lineWidth));
                lineWidth += advance(c);
                i++;
                continue;
            }

            // Measure the whole word ahead so it can wrap as a unit.
            int end = i;
            float wordWidth = 0f;
            while (end < length && !isBreak(text.get(end))) {
                wordWidth += advance(text.get(end));
                end++;
            }

            boolean wordFitsOnOneLine = wordWidth <= lineBudget;
            if (lineWidth > 0f && wordFitsOnOneLine && lineWidth + wordWidth > lineBudget) {
                row++;
                lineWidth = 0f;
            }

            for (int k = i; k < end && row < maxRows; k++) {
                TextVisualizers.VisualChar wc = text.get(k);
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

    /** A space or newline as written ends a word; a pixel never does, since it is drawn. */
    private static boolean isBreak(TextVisualizers.VisualChar c) {
        return c.source() == '\n' || (c.source() == ' ' && !c.pixel());
    }

    private float advance(TextVisualizers.VisualChar c) {
        if (c.pixel()) {
            return PIXEL_SIZE;
        }
        return this.font.width(String.valueOf(c.glyph()));
    }

}
