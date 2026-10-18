package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;
import g_mungus.zps.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VideoTextVisualizerTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    private static final VideoTextVisualizer RGB = new VideoTextVisualizer(VideoTextVisualizer::rgb);
    private static final VideoTextVisualizer HUE = new VideoTextVisualizer(VideoTextVisualizer::hue);

    @Test
    void codesRunFromSpaceToUnderscoreWithLowercaseFolded() {
        assertEquals(0, VideoTextVisualizer.code(' '));
        assertEquals(1, VideoTextVisualizer.code('!'));
        assertEquals(63, VideoTextVisualizer.code('_'));
        assertEquals(VideoTextVisualizer.code('A'), VideoTextVisualizer.code('a'));
        assertEquals(VideoTextVisualizer.code('Z'), VideoTextVisualizer.code('z'));
        assertEquals(VideoTextVisualizer.code('@'), VideoTextVisualizer.code('`'));
        assertEquals(VideoTextVisualizer.code('^'), VideoTextVisualizer.code('~'));
    }

    @Test
    void rgbIsTwoBitsPerChannel() {
        assertEquals(0x000000, RGB.color(' '));
        assertEquals(0x0000FF, RGB.color('#'));
        assertEquals(0x550000, RGB.color('0'));
        assertEquals(0xFF0000, RGB.color('P'));
        assertEquals(0xFFFF00, RGB.color('\\'));
        assertEquals(0xFFFFFF, RGB.color('_'));
    }

    @Test
    void rgbGivesAllSixtyFourColours() {
        Set<Integer> colours = new HashSet<>();
        for (char c = ' '; c <= '_'; c++) {
            colours.add(RGB.color(c));
        }
        assertEquals(64, colours.size());
    }

    @Test
    void hueRunsFromRedToPurpleOverSixtyFourCodes() {
        assertEquals(0xFF1200, HUE.color('!'));
        assertEquals(0xDAFF00, HUE.color('0'));
        assertEquals(0x00FF48, HUE.color('@'));
        assertEquals(0x0091FF, HUE.color('P'));
        assertEquals(0x7F00FF, HUE.color('_'));
        assertEquals(0xFF0000, HUE.color(' '));
        Set<Integer> colours = new HashSet<>();
        for (char c = ' '; c <= '_'; c++) {
            colours.add(HUE.color(c));
        }
        assertEquals(64, colours.size());
    }

    @Test
    void unprintableCharactersAreBlack() {
        assertEquals(0x000000, RGB.color('é'));
        assertEquals(0x000000, HUE.color('\t'));
    }

    @Test
    void everyCharIsAPixelIgnoringTheDye() {
        List<TextVisualizer.VisualGlyph> glyphs = RGB.visualize("P_ ", DyeColor.RED);
        assertEquals(3, glyphs.size());
        assertTrue(glyphs.stream().allMatch(TextVisualizer.VisualGlyph::pixel));
        assertEquals(0xFF0000, glyphs.get(0).color());
    }

    @Test
    void onlyFullDisplayPicturesArePlaced() {
        assertNotNull(RGB.place("x".repeat(12 * 12), 28, 28));
        assertNotNull(RGB.place("x".repeat(28 * 28), 28, 28));
        assertNotNull(RGB.place("x".repeat(44 * 44), 28, 28));
        // Not one of the three sizes: laid out as text.
        assertNull(RGB.place("x".repeat(30 * 30), 28, 28));
        assertNull(RGB.place("x".repeat(44 * 22), 44, 12));
        assertNull(RGB.place("x".repeat(44 * 44 - 1), 28, 28));
        // Rows must all be as long as there are rows.
        assertNull(RGB.place(("x".repeat(12) + "\n").repeat(11) + "x".repeat(11), 12, 12));
        assertNull(RGB.place(("x".repeat(12) + "\n").repeat(13), 12, 12));
    }

    @Test
    void biggerPictureIsCroppedToItsMiddle() {
        // 44x44 on a 28x28 display: 8 columns and rows cut off each side.
        TextVisualizer.Cell[] cells = RGB.place("x".repeat(44 * 44), 28, 28);
        assertEquals(new TextVisualizer.Cell(-8, -8), cells[0]);
        assertEquals(new TextVisualizer.Cell(0, 0), cells[8 * 44 + 8]);
        assertEquals(new TextVisualizer.Cell(27, 27), cells[35 * 44 + 35]);
    }

    @Test
    void smallerPictureIsCentred() {
        TextVisualizer.Cell[] cells = RGB.place("x".repeat(12 * 12), 44, 44);
        assertEquals(new TextVisualizer.Cell(16, 16), cells[0]);
        assertEquals(new TextVisualizer.Cell(27, 27), cells[143]);
    }

    @Test
    void lineBreaksMakeTheRows() {
        // Twelve rows of twelve, a trailing break adding no row, centred on a 28x28 display.
        TextVisualizer.Cell[] cells = RGB.place(("x".repeat(12) + "\n").repeat(12), 28, 28);
        assertEquals(new TextVisualizer.Cell(8, 8), cells[0]);
        assertNull(cells[12]);
        assertEquals(new TextVisualizer.Cell(8, 9), cells[13]);
        assertEquals(new TextVisualizer.Cell(19, 19), cells[11 * 13 + 11]);
    }
}
