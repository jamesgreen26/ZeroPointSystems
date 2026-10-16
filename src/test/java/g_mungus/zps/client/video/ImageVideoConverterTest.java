package g_mungus.zps.client.video;

import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageVideoConverterTest {

    private static final int PIXELS = ImageVideoConverter.SIZE * ImageVideoConverter.SIZE;

    /** One frame of a test GIF: a picture drawn at an offset, shown for a while, then disposed of. */
    private record TestFrame(BufferedImage image, int left, int top, int delayCs, String disposal) {
        TestFrame(BufferedImage image) {
            this(image, 0, 0, 5, "none");
        }
    }

    private static BufferedImage solid(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    /** A palette colour: two bits each of red, green and blue. */
    private static int level(int red, int green, int blue) {
        return red * 85 << 16 | green * 85 << 8 | blue * 85;
    }

    private static byte[] gif(List<TestFrame> frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (TestFrame frame : frames) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(frame.image()), param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", frame.disposal());
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(frame.delayCs()));
                control.setAttribute("transparentColorIndex", "0");
                IIOMetadataNode descriptor = node(root, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", Integer.toString(frame.left()));
                descriptor.setAttribute("imageTopPosition", Integer.toString(frame.top()));
                descriptor.setAttribute("imageWidth", Integer.toString(frame.image().getWidth()));
                descriptor.setAttribute("imageHeight", Integer.toString(frame.image().getHeight()));
                descriptor.setAttribute("interlaceFlag", "FALSE");
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame.image(), null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) {
                return (IIOMetadataNode) child;
            }
        }
        IIOMetadataNode made = new IIOMetadataNode(name);
        root.appendChild(made);
        return made;
    }

    private static ImageVideoConverter.Result convert(List<TestFrame> frames) throws IOException {
        return ImageVideoConverter.convert(new ByteArrayInputStream(gif(frames)));
    }

    /** The pixels of one script line, tags taken off. */
    private static String pixels(String line) {
        assertTrue(line.startsWith(ImageVideoConverter.OPEN_TAG) && line.endsWith(ImageVideoConverter.CLOSE_TAG), line);
        return line.substring(ImageVideoConverter.OPEN_TAG.length(), line.length() - ImageVideoConverter.CLOSE_TAG.length());
    }

    @Test
    void picksFramesSpreadEvenlyFromTheFirst() {
        assertArrayEquals(new int[]{0, 1, 2}, ImageVideoConverter.pickFrames(3, 16));
        assertArrayEquals(new int[]{0, 1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 13, 15, 16, 17, 18},
                ImageVideoConverter.pickFrames(20, 16));
        assertArrayEquals(new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30},
                ImageVideoConverter.pickFrames(32, 16));
    }

    @Test
    void pixelsTakeTheNearestPaletteColour() {
        assertEquals(' ', ImageVideoConverter.pixel(0x000000));
        assertEquals('_', ImageVideoConverter.pixel(0xFFFFFF));
        assertEquals('P', ImageVideoConverter.pixel(0xFF0000));
        // 0x30 is nearer 0x55 than 0x00, so it rounds up to level 1.
        assertEquals('!', ImageVideoConverter.pixel(0x000030));
        assertEquals(' ', ImageVideoConverter.pixel(0x000020));
    }

    @Test
    void keepsSixteenFramesOfTwentyAtTheGifsSpeed() throws IOException {
        List<TestFrame> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            frames.add(new TestFrame(solid(44, 44, level(i % 4, i / 4 % 4, i / 16))));
        }
        ImageVideoConverter.Result result = convert(frames);

        assertEquals(20, result.sourceFrames());
        assertEquals(16, result.frames());
        // 20 frames of 5 hundredths is a second, which is 20 ticks over 16 frames.
        assertEquals(1.25, result.ticksPerFrame(), 1e-9);
        String[] lines = result.script().split("\n", -1);
        assertEquals(16, lines.length);
        int[] picks = ImageVideoConverter.pickFrames(20, 16);
        for (int i = 0; i < lines.length; i++) {
            int source = picks[i];
            char expected = ImageVideoConverter.pixel(level(source % 4, source / 4 % 4, source / 16));
            assertEquals(String.valueOf(expected).repeat(PIXELS), pixels(lines[i]), "frame " + i);
        }
    }

    @Test
    void sixteenFullFramesFitInATerminal() throws IOException {
        List<TestFrame> frames = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            frames.add(new TestFrame(solid(10, 10, level(3, 3, 3))));
        }
        String script = convert(frames).script();
        assertEquals(16 * (ImageVideoConverter.OPEN_TAG.length() + PIXELS + ImageVideoConverter.CLOSE_TAG.length()) + 15,
                script.length());
        assertTrue(script.length() <= ScriptTerminalBlockEntity.MAX_SCRIPT_LENGTH);
    }

    @Test
    void wideFramesAreLetterboxedInBlack() throws IOException {
        String line = pixels(convert(List.of(new TestFrame(solid(88, 44, level(3, 0, 0))))).script());
        // 88x44 fits as 44x22, with 11 black rows above and below.
        for (int y = 0; y < 44; y++) {
            String row = line.substring(y * 44, y * 44 + 44);
            char expected = y >= 11 && y < 33 ? 'P' : ' ';
            assertEquals(String.valueOf(expected).repeat(44), row, "row " + y);
        }
    }

    @Test
    void averagesWhenShrinking() throws IOException {
        // Each display pixel covers a 2x2 block of one white and three black pixels: a quarter grey.
        BufferedImage checker = new BufferedImage(88, 88, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 88; y += 2) {
            for (int x = 0; x < 88; x += 2) {
                checker.setRGB(x, y, 0xFFFFFF);
            }
        }
        String line = pixels(convert(List.of(new TestFrame(checker))).script());
        // 255 / 4 = 63, nearest level 1 (85) on every channel.
        assertEquals(String.valueOf(ImageVideoConverter.pixel(level(1, 1, 1))).repeat(PIXELS), line);
    }

    @Test
    void partialFramesDrawOverWhatCameBefore() throws IOException {
        List<TestFrame> frames = List.of(
                new TestFrame(solid(44, 44, level(0, 0, 3))),
                new TestFrame(solid(22, 22, level(3, 0, 0)), 22, 22, 5, "none"),
                // Shown over the red corner, then restored to it.
                new TestFrame(solid(44, 44, level(0, 3, 0)), 0, 0, 5, "restoreToPrevious"),
                new TestFrame(solid(1, 1, level(3, 3, 3)), 0, 0, 5, "none"));
        String[] lines = convert(frames).script().split("\n", -1);

        String corner = pixels(lines[1]);
        assertEquals('#', corner.charAt(0), "blue kept where frame 2 did not draw");
        assertEquals('P', corner.charAt(43 * 44 + 43), "red drawn at frame 2's offset");
        assertEquals(String.valueOf(',').repeat(PIXELS), pixels(lines[2]), "green over everything");

        String restored = pixels(lines[3]);
        assertEquals('_', restored.charAt(0), "white dot drawn after the green was undone");
        assertEquals('#', restored.charAt(1), "blue back where the green was");
        assertEquals('P', restored.charAt(43 * 44 + 43), "red back where the green was");
    }

    private static byte[] still(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes), "no writer for " + format);
        return bytes.toByteArray();
    }

    @Test
    void pngIsOneLetterboxedFrame() throws IOException {
        ImageVideoConverter.Result result = ImageVideoConverter.convert(
                new ByteArrayInputStream(still(solid(30, 60, level(0, 0, 3)), "png")));
        assertEquals(ImageVideoConverter.Format.PNG, result.format());
        assertEquals(1, result.frames());
        assertEquals(0, result.ticksPerFrame());
        String line = pixels(result.script());
        // 30x60 fits as 22x44, with 11 black columns either side.
        String row = line.substring(0, 44);
        assertEquals(" ".repeat(11) + "#".repeat(22) + " ".repeat(11), row);
    }

    @Test
    void jpegIsOneFrame() throws IOException {
        // JPEG is lossy, but a flat colour stays close enough to land on the same palette colour.
        ImageVideoConverter.Result result = ImageVideoConverter.convert(
                new ByteArrayInputStream(still(solid(64, 64, level(3, 0, 0)), "jpg")));
        assertEquals(ImageVideoConverter.Format.JPEG, result.format());
        assertEquals("P".repeat(PIXELS), pixels(result.script()));
    }

    @Test
    void refusesOtherFormats() throws IOException {
        byte[] bmp = still(solid(4, 4, 0), "bmp");
        IOException failure = assertThrows(IOException.class,
                () -> ImageVideoConverter.convert(new ByteArrayInputStream(bmp)));
        assertEquals("Not a GIF, MP4, PNG or JPEG", failure.getMessage());
    }

    @Test
    void refusesHugeImagesBeforeDecodingThem() throws IOException {
        byte[] png = still(new BufferedImage(ImageVideoConverter.MAX_SOURCE_SIDE + 1, 1, BufferedImage.TYPE_INT_RGB), "png");
        assertThrows(IOException.class, () -> ImageVideoConverter.convert(new ByteArrayInputStream(png)));
    }

    /**
     * Twenty frames at 10 fps, each a flat palette colour, made with ffmpeg in the given H.264
     * profile. Video compression is lossy, but a flat colour lands on the same palette colour.
     */
    private static void checkColourVideo(String resource) throws IOException {
        ImageVideoConverter.Result result;
        try (var in = ImageVideoConverterTest.class.getResourceAsStream(resource)) {
            result = ImageVideoConverter.convert(in);
        }
        assertEquals(ImageVideoConverter.Format.MP4, result.format());
        assertEquals(20, result.sourceFrames());
        assertEquals(16, result.frames());
        // Two seconds over 16 frames is 2.5 ticks each.
        assertEquals(2.5, result.ticksPerFrame(), 1e-6);
        String[] lines = result.script().split("\n", -1);
        int[] picks = ImageVideoConverter.pickFrames(20, 16);
        for (int i = 0; i < lines.length; i++) {
            int source = picks[i];
            char colour = ImageVideoConverter.pixel(level(source % 4, source / 4 % 4, source / 16));
            // 64x48 fits as 44x33, with black rows above and below.
            String expected = " ".repeat(5 * 44) + String.valueOf(colour).repeat(33 * 44) + " ".repeat(6 * 44);
            assertEquals(expected, pixels(lines[i]), "frame " + i + " (source frame " + source + ")");
        }
    }

    @Test
    void baselineMp4KeepsSixteenFramesOfTwenty() throws IOException {
        checkColourVideo("/video/colours_baseline.mp4");
    }

    @Test
    void highProfileMp4KeepsSixteenFramesOfTwenty() throws IOException {
        checkColourVideo("/video/colours_high.mp4");
    }

    @Test
    void refusesWhatIsNotAnImage() {
        assertThrows(IOException.class,
                () -> ImageVideoConverter.convert(new ByteArrayInputStream("not a gif".getBytes())));
    }
}
