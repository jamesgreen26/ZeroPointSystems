package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextVisualizersTest {

    private static final int SHOUT_COLOR = 0x123456;

    /** Draws every char upper-cased in its own colour, so its runs are easy to pick out. */
    private static final TextVisualizer SHOUT = (text, dye) -> {
        List<TextVisualizer.VisualGlyph> glyphs = new ArrayList<>();
        for (char c : text.toCharArray()) {
            glyphs.add(new TextVisualizer.VisualGlyph(Character.toUpperCase(c), SHOUT_COLOR));
        }
        return glyphs;
    };

    private static final Map<String, TextVisualizer> VISUALIZERS =
            Map.of(TextVisualizers.DEFAULT_NAME, TextVisualizers.DEFAULT, "shout", SHOUT);

    /** The glyphs drawn; the shout visualizer's show as upper-case. */
    private static String drawn(String text) {
        return TextVisualizers.visualize(text, DyeColor.WHITE, VISUALIZERS).stream()
                .map(c -> String.valueOf(c.glyph()))
                .collect(Collectors.joining());
    }

    @Test
    void plainTextIsDrawnAsWritten() {
        assertEquals("text <b> text", drawn("text <b> text"));
    }

    @Test
    void taggedRunGoesToItsVisualizerAndTagsAreDropped() {
        assertEquals("a SET_REDSTONE 15 b", drawn("a <shout>set_redstone 15</shout> b"));
    }

    @Test
    void taggedRunKeepsSourceCharsAndColours() {
        List<TextVisualizers.VisualChar> chars = TextVisualizers.visualize("x<shout>y</shout>", DyeColor.RED, VISUALIZERS);
        assertEquals(2, chars.size());
        assertEquals(new TextVisualizers.VisualChar('x', 'x', DyeColor.RED.getTextColor()), chars.get(0));
        assertEquals(new TextVisualizers.VisualChar('y', 'Y', SHOUT_COLOR), chars.get(1));
    }

    @Test
    void severalTagsAndNewlines() {
        assertEquals("a\nB\nc D", drawn("a\n<shout>b\n</shout>c <shout>d</shout>"));
    }

    @Test
    void unknownOrUnclosedTagsAreDrawnAsWritten() {
        assertEquals("<nope>x</nope>", drawn("<nope>x</nope>"));
        assertEquals("<shout>x", drawn("<shout>x"));
        assertEquals("1 < 2 X", drawn("1 < 2 <shout>x</shout>"));
    }

    /** The first closing tag ends the run; an inner opening tag is just text to the visualizer. */
    @Test
    void tagsDoNotNest() {
        assertEquals("<SHOUT>A</shout>b", drawn("<shout><shout>a</shout></shout>b"));
    }
}
