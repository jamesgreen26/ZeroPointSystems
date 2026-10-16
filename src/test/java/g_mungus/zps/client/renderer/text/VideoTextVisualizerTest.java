package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VideoTextVisualizerTest {

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
}
