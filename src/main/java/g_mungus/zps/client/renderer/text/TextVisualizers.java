package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The text visualizers a text display can use, by tag name, and the reading of those tags.
 *
 * <p>Text outside any tag uses {@link #DEFAULT}. {@code <name>...</name>}, where {@code name} is
 * registered, hands what is between the tags to that visualizer and draws no tags. Anything else
 * that looks like a tag (an unknown name, or an opening tag never closed) is drawn as written.
 * Tags do not nest: inside one, text is the visualizer's to read as it likes.
 */
public final class TextVisualizers {

    public static final String DEFAULT_NAME = "default";
    public static final TextVisualizer DEFAULT = new DefaultTextVisualizer();

    private static final Map<String, TextVisualizer> REGISTRY = new HashMap<>();

    static {
        register(DEFAULT_NAME, DEFAULT);
        register("munguscript", new MungusScriptTextVisualizer());
        register("video_rgb", new VideoTextVisualizer(VideoTextVisualizer::rgb));
        register("video_hue", new VideoTextVisualizer(VideoTextVisualizer::hue));
    }

    private TextVisualizers() {
    }

    /** Makes {@code <name>...</name>} use {@code visualizer}, replacing any visualizer already called that. */
    public static void register(String name, TextVisualizer visualizer) {
        REGISTRY.put(name, visualizer);
    }

    /**
     * One char of display text after its tags are read: the char as written and how to draw it.
     *
     * @see TextVisualizer.VisualGlyph
     */
    public record VisualChar(char source, char glyph, int color, boolean pixel) {
        public VisualChar(char source, char glyph, int color) {
            this(source, glyph, color, false);
        }
    }

    /** The text with its tags read and dropped, each remaining char given its look. */
    public static List<VisualChar> visualize(String text, DyeColor dye) {
        return visualize(text, dye, REGISTRY);
    }

    /** {@link #visualize(String, DyeColor)} against the given visualizers. */
    public static List<VisualChar> visualize(String text, DyeColor dye, Map<String, TextVisualizer> visualizers) {
        TextVisualizer fallback = visualizers.getOrDefault(DEFAULT_NAME, DEFAULT);
        List<VisualChar> out = new ArrayList<>(text.length());
        int plainStart = 0;
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('<', at);
            if (open == -1) {
                break;
            }
            int openEnd = text.indexOf('>', open + 1);
            if (openEnd == -1) {
                break;
            }
            String name = text.substring(open + 1, openEnd);
            TextVisualizer visualizer = visualizers.get(name);
            String closeTag = "</" + name + ">";
            int close = visualizer == null ? -1 : text.indexOf(closeTag, openEnd + 1);
            if (close == -1) {
                at = open + 1;
                continue;
            }
            append(out, text.substring(plainStart, open), dye, fallback);
            append(out, text.substring(openEnd + 1, close), dye, visualizer);
            at = close + closeTag.length();
            plainStart = at;
        }
        append(out, text.substring(plainStart), dye, fallback);
        return out;
    }

    private static void append(List<VisualChar> out, String run, DyeColor dye, TextVisualizer visualizer) {
        if (run.isEmpty()) {
            return;
        }
        List<TextVisualizer.VisualGlyph> glyphs = visualizer.visualize(run, dye);
        if (glyphs.size() != run.length()) {
            // A visualizer that lost count; draw the run plainly rather than misplace it.
            glyphs = DEFAULT.visualize(run, dye);
        }
        for (int i = 0; i < run.length(); i++) {
            TextVisualizer.VisualGlyph glyph = glyphs.get(i);
            out.add(new VisualChar(run.charAt(i), glyph.glyph(), glyph.color(), glyph.pixel()));
        }
    }
}
