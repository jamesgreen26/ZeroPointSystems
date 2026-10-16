package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Decides how a run of text display text looks: which glyph each character is drawn as, and in
 * what colour. A visualizer sees its whole run at once, so it can colour by context, as script
 * highlighting must. Lighting is not its concern; the display lights every visualizer alike.
 *
 * <p>Visualizers are registered by name in {@link TextVisualizers}, and text picks one by
 * wrapping a part of itself in a tag of that name: {@code <munguscript>set_redstone 15</munguscript>}.
 */
@FunctionalInterface
public interface TextVisualizer {

    /**
     * How one character is drawn. {@code color} is RGB. A {@code pixel} is not a glyph from the
     * font but a filled square one row high, and is never a word break, even for a space.
     */
    record VisualGlyph(char glyph, int color, boolean pixel) {
        public VisualGlyph(char glyph, int color) {
            this(glyph, color, false);
        }

        /** A filled square of {@code color}. */
        public static VisualGlyph pixel(int color) {
            return new VisualGlyph(' ', color, true);
        }
    }

    /**
     * One {@link VisualGlyph} for each character of {@code text}, newlines included, in order.
     *
     * @param dye the display's dye colour
     */
    List<VisualGlyph> visualize(String text, DyeColor dye);

    /** A place on a display's grid of square cells, each one text row high and as wide. Row 0 is the top. */
    record Cell(int column, int row) {
    }

    /**
     * Where each char of {@code text} goes when this visualizer's tag holds all the display shows,
     * on a display {@code columns} by {@code rows} cells big; or null to lay the text out as words,
     * as text usually is. A char with no cell, or with a cell off the display, is not drawn. This is
     * how a visualizer decides what part of its text shows when there is too much for the display.
     */
    default @Nullable Cell[] place(String text, int columns, int rows) {
        return null;
    }
}
