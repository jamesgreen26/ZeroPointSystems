package g_mungus.zps.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The layout every ZPS machine page in JEI shares, after Create's processing pages: inputs on the left, outputs on the
 * right, the machine drawn in 3D over a soft shadow between them with a long arrow running under it from one to the
 * other, and the recipe's figures on stippled bars beneath. Drawn by hand, not from Create's sheet.
 *
 * <p>Slots sit in a grid on each side, as many columns as asked, growing inward from the edge and downward from the
 * arrow's row; the page grows to fit the rows and the bars. Slot positions are where the ingredient is drawn; the
 * slot background goes one pixel up and left of it, as JEI and Kelvin place it.
 *
 * @param inputs        how many input slots
 * @param inputColumns  how many of them to a row
 * @param outputs       how many output slots
 * @param outputColumns how many of them to a row
 * @param bars          how many figure bars beneath
 */
record MachinePage(int inputs, int inputColumns, int outputs, int outputColumns, int bars) {

    /** Kelvin's width, so these line up with its gas reaction pages. */
    static final int WIDTH = 177;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_PITCH = 19;
    /** The top of the first slot row: the arrow runs through its middle. */
    private static final int SLOT_TOP = 38;
    /** How far a single column sits in from the edge; each further column moves the grid out by {@link #INSET_STEP}. */
    private static final int SLOT_INSET = 20;
    private static final int INSET_STEP = 8;
    private static final int MIN_INSET = 4;

    private static final int ARROW_Y = SLOT_TOP + 7;
    private static final int ARROW_GAP = 6;
    private static final int ARROW_HEAD = 4;
    private static final int ARROW_COLOR = 0xFFA0A0A0;
    /**
     * A label on the arrow is drawn at three-quarter size just under the shaft and its shadow, in the slot row's
     * height, so it takes no room of its own.
     */
    private static final float ARROW_LABEL_SCALE = 0.75f;
    private static final int ARROW_LABEL_Y = ARROW_Y + 4;
    private static final int ARROW_SHADOW_COLOR = 0xFF4A4A4A;

    private static final int MACHINE_X = WIDTH / 2;
    private static final int SHADOW_WIDTH = 44;
    private static final int SHADOW_HEIGHT = 10;
    private static final int SHADOW_Y = 29;
    private static final int SHADOW_ALPHA = 0x50;

    /** The scale a block is drawn at, and where the centre of the machine's block sits. */
    private static final float BLOCK_SCALE = 22;
    private static final int BLOCK_Y = 18;

    private static final int BAR_GAP = 4;
    private static final int BAR_PITCH = 20;
    private static final int BAR_LEFT = 4;
    private static final int BAR_WIDTH = WIDTH - 2 * BAR_LEFT;
    private static final int BAR_HEIGHT = 19;
    private static final int BAR_BORDER = 2;
    private static final int BAR_COLOR = 0xFF363636;
    private static final int BAR_TEXT_X = 7;
    private static final int BAR_TEXT_Y_OFFSET = 5;
    private static final int BAR_TEXT_COLOR = 0xFFFFFF;

    /** Draws in a block's own space, its corner at the origin, into {@code buffers}. */
    @FunctionalInterface
    interface BlockScene {
        void render(PoseStack pose, MultiBufferSource buffers);
    }

    // --- layout ------------------------------------------------------------------------------

    int inputX(int index) {
        return inset(inputColumns) + (index % inputColumns) * SLOT_PITCH;
    }

    int inputY(int index) {
        return SLOT_TOP + (index / inputColumns) * SLOT_PITCH;
    }

    int outputX(int index) {
        return WIDTH - inset(outputColumns) - SLOT_SIZE - (index % outputColumns) * SLOT_PITCH;
    }

    int outputY(int index) {
        return SLOT_TOP + (index / outputColumns) * SLOT_PITCH;
    }

    private static int inset(int columns) {
        return Math.max(MIN_INSET, SLOT_INSET - (columns - 1) * INSET_STEP);
    }

    private static int rows(int count, int columns) {
        return Math.max(1, (count + columns - 1) / columns);
    }

    private int barsTop() {
        int rows = Math.max(rows(inputs, inputColumns), rows(outputs, outputColumns));
        return SLOT_TOP + rows * SLOT_PITCH + 1 + BAR_GAP;
    }

    int height() {
        return bars == 0 ? barsTop() - BAR_GAP : barsTop() + bars * BAR_PITCH;
    }

    // --- drawing -----------------------------------------------------------------------------

    /** The arrow from the inputs to the outputs and the shadow the machine stands on; draw first. */
    void drawStage(GuiGraphics graphics) {
        int left = inset(inputColumns) + (Math.min(inputs, inputColumns) - 1) * SLOT_PITCH + SLOT_SIZE + ARROW_GAP;
        int right = WIDTH - inset(outputColumns) - SLOT_SIZE - (Math.min(outputs, outputColumns) - 1) * SLOT_PITCH
                - ARROW_GAP;
        drawArrow(graphics, left, right, 1, ARROW_SHADOW_COLOR);
        drawArrow(graphics, left, right, 0, ARROW_COLOR);
        drawShadow(graphics);
    }

    private void drawArrow(GuiGraphics graphics, int left, int right, int offset, int color) {
        int tip = right + offset;
        int y = ARROW_Y + offset;
        graphics.fill(left + offset, y, tip - ARROW_HEAD, y + 2, color);
        // The head: a column per pixel, narrowing to the tip.
        for (int i = 0; i < ARROW_HEAD + 1; i++) {
            int half = ARROW_HEAD - i;
            graphics.fill(tip - ARROW_HEAD - 1 + i, y - half + 1, tip - ARROW_HEAD + i, y + 1 + half, color);
        }
    }

    /** A soft dark ellipse under the machine, darkest in the middle. */
    private void drawShadow(GuiGraphics graphics) {
        double rx = SHADOW_WIDTH / 2.0;
        double ry = SHADOW_HEIGHT / 2.0;
        int left = MACHINE_X - SHADOW_WIDTH / 2;
        for (int y = 0; y < SHADOW_HEIGHT; y++) {
            for (int x = 0; x < SHADOW_WIDTH; x++) {
                double dx = (x + 0.5 - rx) / rx;
                double dy = (y + 0.5 - ry) / ry;
                double d = dx * dx + dy * dy;
                if (d < 1) {
                    int alpha = (int) (SHADOW_ALPHA * (1 - d));
                    graphics.fill(left + x, SHADOW_Y + y, left + x + 1, SHADOW_Y + y + 1, alpha << 24);
                }
            }
        }
    }

    /** One block, drawn as {@link #drawMachine} draws a scene. */
    void drawBlock(GuiGraphics graphics, BlockState state) {
        drawMachine(graphics, (pose, buffers) -> renderBlock(pose, buffers, state));
    }

    /**
     * A machine in 3D over the shadow, tilted to show its top and turned so its east face points at the outputs. The
     * scene's block at the origin stands on the shadow; anything above it may reach past the top of the page.
     */
    void drawMachine(GuiGraphics graphics, BlockScene scene) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(MACHINE_X, BLOCK_Y, 100);
        pose.scale(BLOCK_SCALE, -BLOCK_SCALE, BLOCK_SCALE);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(-35));
        pose.translate(-0.5f, -0.5f, -0.5f);

        Lighting.setupFor3DItems();
        scene.render(pose, graphics.bufferSource());
        graphics.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }

    static void renderBlock(PoseStack pose, MultiBufferSource buffers, BlockState state) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    /** {@code text}, small, under the arrow beneath the machine. */
    void drawArrowLabel(GuiGraphics graphics, Component text) {
        var font = Minecraft.getInstance().font;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(MACHINE_X - font.width(text) * ARROW_LABEL_SCALE / 2, ARROW_LABEL_Y, 200);
        pose.scale(ARROW_LABEL_SCALE, ARROW_LABEL_SCALE, 1);
        graphics.drawString(font, text, 0, 0, BAR_TEXT_COLOR);
        pose.popPose();
    }

    /** Figure bar {@code index}, counting from the top, with {@code text} on it. */
    void drawBar(GuiGraphics graphics, int index, Component text) {
        int top = barsTop() + index * BAR_PITCH;
        drawBarOutline(graphics, BAR_LEFT, top);
        graphics.drawString(Minecraft.getInstance().font, text, BAR_TEXT_X, top + BAR_TEXT_Y_OFFSET, BAR_TEXT_COLOR);
    }

    /**
     * Kelvin's requirement bar, which is Create's "darker bar" drawn by hand: a two pixel border stippled on a
     * checkerboard, so the outline reads soft instead of as a hard frame.
     */
    private static void drawBarOutline(GuiGraphics graphics, int left, int top) {
        for (int y = 0; y < BAR_HEIGHT; y++) {
            boolean onHorizontalEdge = y < BAR_BORDER || y >= BAR_HEIGHT - BAR_BORDER;
            for (int x = 0; x < BAR_WIDTH; x++) {
                boolean onVerticalEdge = x < BAR_BORDER || x >= BAR_WIDTH - BAR_BORDER;
                if ((onHorizontalEdge || onVerticalEdge) && (x + y) % 2 != 0) {
                    graphics.fill(left + x, top + y, left + x + 1, top + y + 1, BAR_COLOR);
                }
            }
        }
    }
}
