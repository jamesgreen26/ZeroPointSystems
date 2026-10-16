package g_mungus.zps.client.renderer.text;

import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

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
