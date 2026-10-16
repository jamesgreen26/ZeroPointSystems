package g_mungus.zps.client.renderer.text;

import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Every character as one square pixel, coloured by its character code, so a script can draw
 * pictures a row per line. The dye plays no part.
 *
 * <p>Colours come from 64 codes: space to {@code _} in order, with {@code `}, lowercase letters and
 * {@code {|}~} taking the colour of the character 32 below them ({@code a} as {@code A}), since
 * block and item ids are lowercase. Anything that is not printable ASCII is black: an unlit pixel.
 */
public final class VideoTextVisualizer implements TextVisualizer {

    public static final int BLACK = 0x000000;

    /** The colour for each of the 64 codes, 0 to 63. */
    private final IntUnaryOperator palette;

    public VideoTextVisualizer(IntUnaryOperator palette) {
        this.palette = palette;
    }

    @Override
    public List<VisualGlyph> visualize(String text, DyeColor dye) {
        List<VisualGlyph> glyphs = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            glyphs.add(VisualGlyph.pixel(color(text.charAt(i))));
        }
        return glyphs;
    }

    /** The sides of the pictures that fill a 1x1, 2x2 and 3x3 display. */
    static final int[] PICTURE_SIDES = {12, 28, 44};

    /**
     * A picture the size of a full 1x1, 2x2 or 3x3 display, centred on this one: when it is
     * bigger, the middle of it shows. The picture is either that many pixels with no line breaks,
     * or rows of that many pixels, one per line. Anything else is laid out as text.
     */
    @Override
    public @Nullable Cell[] place(String text, int columns, int rows) {
        String[] lines = text.split("\n", -1);
        int lineCount = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        int side = -1;
        for (int candidate : PICTURE_SIDES) {
            boolean fits = lines.length == 1
                    ? text.length() == candidate * candidate
                    : lineCount == candidate && allLength(lines, lineCount, candidate);
            if (fits) {
                side = candidate;
            }
        }
        if (side == -1) {
            return null;
        }
        int left = Math.floorDiv(columns - side, 2);
        int top = Math.floorDiv(rows - side, 2);

        Cell[] cells = new Cell[text.length()];
        int x = 0;
        int y = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                x = 0;
                y++;
                continue;
            }
            if (x == side) {
                x = 0;
                y++;
            }
            cells[i] = new Cell(left + x, top + y);
            x++;
        }
        return cells;
    }

    private static boolean allLength(String[] lines, int count, int length) {
        for (int i = 0; i < count; i++) {
            if (lines[i].length() != length) {
                return false;
            }
        }
        return true;
    }

    public int color(char c) {
        int code = code(c);
        return code < 0 ? BLACK : palette.applyAsInt(code);
    }

    /** Which of the 64 codes {@code c} is, or -1 for a black pixel. */
    public static int code(char c) {
        if (c < ' ' || c > '~') {
            return -1;
        }
        int folded = c >= '`' ? c - 32 : c;
        return folded - ' ';
    }

    /** Two bits each of red, green and blue, in that order from the top: levels 0, 85, 170, 255. */
    public static int rgb(int code) {
        int red = (code >> 4) & 3;
        int green = (code >> 2) & 3;
        int blue = code & 3;
        return (red * 85) << 16 | (green * 85) << 8 | blue * 85;
    }

    /**
     * All six bits pick one of 64 hues, at full saturation and brightness, evenly over the 270
     * degrees from red at 0 to purple at 63. Leaving the last quarter of the wheel out keeps the
     * two ends apart, so the codes read as a scale rather than a loop.
     */
    public static int hue(int code) {
        return Mth.hsvToRgb(code / 63f * 0.75f, 1f, 1f) & 0xFFFFFF;
    }
}
