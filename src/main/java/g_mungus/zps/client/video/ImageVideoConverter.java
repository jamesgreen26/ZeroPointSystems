package g_mungus.zps.client.video;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;
import org.jcodec.common.model.Size;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Turns a GIF, MP4, PNG or JPEG into a script of {@code video_rgb} or {@code video_hue} frames
 * for a 3x3 text display: one frame per line, each 44x44 pixels wrapped in its tag, so a reader can show frame {@code f}
 * with {@code script_contents get_line value_of(f + 2)}. A PNG or JPEG is one frame.
 *
 * <p>GIF frames are rebuilt as the GIF draws them, since a GIF frame may change only part of the
 * picture. MP4s are decoded with JCodec, which reads H.264 video. Up to {@link #MAX_FRAMES} frames
 * are kept, spread as evenly as possible over the animation.
 * Each frame is fitted inside the square with black bars, averaging the source pixels behind every
 * display pixel, and set in the nearest pixel of the chosen {@link Palette}.
 */
public final class ImageVideoConverter {

    /** A full 3x3 display's pixels along each side. */
    public static final int SIZE = 44;
    /** As many frames as fit in a Script Terminal's 32,500 characters. */
    public static final int MAX_FRAMES = 16;

    /** The text visualizers frames can be written for, and how a colour becomes each one's pixel. */
    public enum Palette {
        /** {@code video_rgb}: the nearest of 64 colours, two bits each of red, green and blue. */
        RGB("video_rgb") {
            @Override
            public char pixel(int rgb) {
                int red = Math.round(((rgb >> 16) & 0xFF) / 85f);
                int green = Math.round(((rgb >> 8) & 0xFF) / 85f);
                int blue = Math.round((rgb & 0xFF) / 85f);
                return (char) (' ' + (red << 4 | green << 2 | blue));
            }
        },
        /**
         * {@code video_hue}: the nearest of 64 hues from red to purple, at full brightness, so only
         * the colour's hue survives. Pinks and magentas, past purple, go to whichever end is nearer.
         * A dark colour is an unlit pixel instead, since the hues have no black.
         */
        HUE("video_hue") {
            @Override
            public char pixel(int rgb) {
                float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                if (hsb[2] < DARK) {
                    return UNLIT;
                }
                float degrees = hsb[0] * 360;
                int code;
                if (degrees <= 270) {
                    code = Math.round(degrees / 270 * 63);
                } else {
                    code = degrees <= 315 ? 63 : 0;
                }
                return (char) (' ' + code);
            }
        };

        /** Below this brightness a hue pixel is unlit. */
        private static final float DARK = 0.25f;
        /** Not printable ASCII, so drawn as an unlit pixel; one a script file holds safely. */
        static final char UNLIT = '\u00B7';

        public final String name;

        Palette(String name) {
            this.name = name;
        }

        public String openTag() {
            return "<" + name + ">";
        }

        public String closeTag() {
            return "</" + name + ">";
        }

        /** The pixel nearest {@code rgb}. */
        public abstract char pixel(int rgb);
    }

    /** Past these the image is refused rather than decoded. */
    static final int MAX_SOURCE_FRAMES = 5000;
    static final int MAX_SOURCE_SIDE = 4096;
    /** What browsers take a GIF frame's delay of 0 or 1 hundredths to mean. */
    private static final int DEFAULT_DELAY_CS = 10;

    private ImageVideoConverter() {
    }

    /** The formats read, by the bytes their files hold at {@code offset}. */
    public enum Format {
        GIF("gif", 0, new byte[]{'G', 'I', 'F', '8'}),
        // An MP4 (or MOV) starts with the size of its first box, then the box's type.
        MP4(null, 4, new byte[]{'f', 't', 'y', 'p'}),
        PNG("png", 0, new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
        JPEG("jpeg", 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        /** Java's image reader for it, or null for a video. */
        private final String readerName;
        private final int offset;
        private final byte[] magic;

        Format(String readerName, int offset, byte[] magic) {
            this.readerName = readerName;
            this.offset = offset;
            this.magic = magic;
        }

        static Format of(byte[] data) throws IOException {
            for (Format format : values()) {
                int end = format.offset + format.magic.length;
                if (data.length >= end && Arrays.equals(data, format.offset, end, format.magic, 0, format.magic.length)) {
                    return format;
                }
            }
            throw new IOException("Not a GIF, MP4, PNG or JPEG");
        }
    }

    /**
     * @param script        the frames, ready to save as a {@code .mungus} file
     * @param format        what the image was
     * @param sourceFrames  how many frames the image has
     * @param frames        how many were kept
     * @param ticksPerFrame how long to show each kept frame, in game ticks, to play at the
     *                      source's speed; 0 for a single still image
     */
    public record Result(String script, Format format, int sourceFrames, int frames, double ticksPerFrame) {
    }

    /** {@link #convert(InputStream, Palette)} for {@code video_rgb}. */
    public static Result convert(InputStream image) throws IOException {
        return convert(image, Palette.RGB);
    }

    public static Result convert(InputStream image, Palette palette) throws IOException {
        byte[] data = image.readAllBytes();
        Format format = Format.of(data);
        if (format == Format.MP4) {
            return convertVideo(data, palette);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) {
                throw new IOException("Could not read the download");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
            if (!readers.hasNext()) {
                throw new IOException("No " + format + " reader available");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                return format == Format.GIF ? convertGif(reader, palette) : convertStill(reader, format, palette);
            } catch (RuntimeException e) {
                // ImageIO's readers throw unchecked exceptions on some malformed files.
                throw new IOException("Not a readable " + format + ": " + e.getMessage(), e);
            } finally {
                reader.dispose();
            }
        }
    }

    /** The first picture of a PNG or JPEG as the only frame. */
    private static Result convertStill(ImageReader reader, Format format, Palette palette) throws IOException {
        checkSize(reader.getWidth(0), reader.getHeight(0));
        BufferedImage image = reader.read(0);
        return new Result(frameLine(fit(image, SIZE), palette), format, 1, 1, 0);
    }

    /**
     * The kept frames of an MP4: the same frames a GIF of as many would keep, found by when they
     * are shown. The video is decoded once, front to back, and each kept frame is the one shown
     * nearest its frame's time, which for a steady frame rate is that frame exactly. Times come
     * from the frames themselves, since H.264 may store frames out of the order they are shown in.
     * Only a frame that is nearer some kept time than any before it is converted.
     */
    private static Result convertVideo(byte[] data, Palette palette) throws IOException {
        try {
            FrameGrab grab = FrameGrab.createFrameGrab(ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(data)));
            DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
            int count = meta.getTotalFrames();
            if (count <= 0) {
                throw new IOException("The video has no frames");
            }
            if (count > MAX_SOURCE_FRAMES) {
                throw new IOException("The video has " + count + " frames; at most " + MAX_SOURCE_FRAMES + " are supported");
            }
            Size size = grab.getMediaInfo().getDim();
            checkSize(size.getWidth(), size.getHeight());

            double duration = meta.getTotalDuration();
            int[] picks = pickFrames(count, MAX_FRAMES);
            int keep = picks.length;
            int[][] kept = new int[keep][];
            double[] distance = new double[keep];
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            double start = Double.NaN;
            PictureWithMetadata frame;
            while ((frame = grab.getNativeFrameWithMetadata()) != null) {
                if (Double.isNaN(start)) {
                    // A keyframe comes first, and is shown first.
                    start = frame.getTimestamp();
                }
                double time = frame.getTimestamp() - start;
                int[] fitted = null;
                for (int i = 0; i < keep; i++) {
                    double off = Math.abs(time - picks[i] * duration / count);
                    if (off < distance[i]) {
                        if (fitted == null) {
                            fitted = fit(toImage(frame.getPicture()), SIZE);
                        }
                        kept[i] = fitted;
                        distance[i] = off;
                    }
                }
            }
            List<int[]> frames = new ArrayList<>(keep);
            for (int[] fitted : kept) {
                if (fitted != null) {
                    frames.add(fitted);
                }
            }
            if (frames.isEmpty()) {
                throw new IOException("No frames could be decoded");
            }
            double ticksPerFrame = duration * 20 / frames.size();
            return new Result(script(frames, palette), Format.MP4, count, frames.size(), ticksPerFrame);
        } catch (JCodecException | RuntimeException e) {
            // JCodec reads only H.264, and throws unchecked exceptions on what it cannot decode.
            throw new IOException("Not a readable MP4 (only H.264 video is supported): " + e.getMessage(), e);
        }
    }

    /** A decoded video picture as an image, cropped to the part the video shows. */
    private static BufferedImage toImage(Picture picture) {
        Picture rgb = picture;
        if (picture.getColor() != ColorSpace.RGB) {
            Transform transform = ColorUtil.getTransform(picture.getColor(), ColorSpace.RGB);
            if (transform == null) {
                throw new IllegalArgumentException("Unsupported colour format " + picture.getColor());
            }
            rgb = Picture.createCropped(picture.getWidth(), picture.getHeight(), ColorSpace.RGB, picture.getCrop());
            transform.transform(picture, rgb);
        }
        // One plane of red, green, blue bytes per pixel, each stored 128 below its value.
        byte[] data = rgb.getPlaneData(0);
        int stride = rgb.getWidth() * 3;
        Rect crop = rgb.getCrop();
        int left = crop == null ? 0 : crop.getX();
        int top = crop == null ? 0 : crop.getY();
        int width = rgb.getCroppedWidth();
        int height = rgb.getCroppedHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int at = (y + top) * stride + left * 3;
            for (int x = 0; x < width; x++, at += 3) {
                int red = data[at] + 128;
                int green = data[at + 1] + 128;
                int blue = data[at + 2] + 128;
                image.setRGB(x, y, red << 16 | green << 8 | blue);
            }
        }
        return image;
    }

    private static void checkSize(int width, int height) throws IOException {
        if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
            throw new IOException("The image is " + width + "x" + height + "; at most " + MAX_SOURCE_SIDE + " a side is supported");
        }
    }

    private static Result convertGif(ImageReader reader, Palette palette) throws IOException {
        int count = reader.getNumImages(true);
        if (count <= 0) {
            throw new IOException("The GIF has no frames");
        }
        if (count > MAX_SOURCE_FRAMES) {
            throw new IOException("The GIF has " + count + " frames; at most " + MAX_SOURCE_FRAMES + " are supported");
        }
        int[] picks = pickFrames(count, MAX_FRAMES);

        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        Node screen = child(root(reader.getStreamMetadata(), "javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
        if (screen != null) {
            width = Math.max(width, intAttribute(screen, "logicalScreenWidth", width));
            height = Math.max(height, intAttribute(screen, "logicalScreenHeight", height));
        }
        checkSize(width, height);

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<int[]> kept = new ArrayList<>(picks.length);
        int nextPick = 0;
        long totalDelayCs = 0;
        Frame previous = null;
        int[] beforePrevious = null;
        int lastPick = picks[picks.length - 1];
        for (int index = 0; index < count; index++) {
            IIOMetadata metadata = reader.getImageMetadata(index);
            if (index > lastPick) {
                // Past the last kept frame only the timing matters, so the picture is not decoded.
                totalDelayCs += Frame.delayCs(metadata);
                continue;
            }
            BufferedImage image = reader.read(index);
            Frame frame = Frame.of(metadata, image);
            totalDelayCs += frame.delayCs();

            if (previous != null) {
                previous.dispose(canvas, beforePrevious);
            }
            beforePrevious = frame.disposal() == Disposal.RESTORE_PREVIOUS
                    ? canvas.getRGB(0, 0, width, height, null, 0, width) : null;
            Graphics2D graphics = canvas.createGraphics();
            graphics.drawImage(image, frame.left(), frame.top(), null);
            graphics.dispose();
            previous = frame;

            if (nextPick < picks.length && picks[nextPick] == index) {
                kept.add(fit(canvas, SIZE));
                nextPick++;
            }
        }

        double ticksPerFrame = totalDelayCs / 100.0 * 20 / kept.size();
        return new Result(script(kept, palette), Format.GIF, count, kept.size(), ticksPerFrame);
    }

    /** The frames one to a line. */
    private static String script(List<int[]> frames, Palette palette) {
        StringBuilder script = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) {
                script.append('\n');
            }
            script.append(frameLine(frames.get(i), palette));
        }
        return script.toString();
    }

    /** Which {@code max} of {@code count} frames to keep, spread evenly and starting with the first. */
    static int[] pickFrames(int count, int max) {
        int kept = Math.min(count, max);
        int[] picks = new int[kept];
        for (int i = 0; i < kept; i++) {
            picks[i] = (int) ((long) i * count / kept);
        }
        return picks;
    }

    /**
     * The picture fitted inside a {@code size} square, centred with black around it, as RGB pixels
     * row by row. Each pixel averages the source pixels it covers, transparent ones counting as black.
     */
    static int[] fit(BufferedImage image, int size) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] source = image.getRGB(0, 0, width, height, null, 0, width);
        double scale = Math.min((double) size / width, (double) size / height);
        int fittedWidth = Math.max(1, (int) Math.round(width * scale));
        int fittedHeight = Math.max(1, (int) Math.round(height * scale));
        int offsetX = (size - fittedWidth) / 2;
        int offsetY = (size - fittedHeight) / 2;

        int[] pixels = new int[size * size];
        for (int y = 0; y < fittedHeight; y++) {
            int y0 = y * height / fittedHeight;
            int y1 = Math.max(y0 + 1, (y + 1) * height / fittedHeight);
            for (int x = 0; x < fittedWidth; x++) {
                int x0 = x * width / fittedWidth;
                int x1 = Math.max(x0 + 1, (x + 1) * width / fittedWidth);
                long red = 0;
                long green = 0;
                long blue = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int argb = source[sy * width + sx];
                        int alpha = argb >>> 24;
                        red += ((argb >> 16) & 0xFF) * alpha;
                        green += ((argb >> 8) & 0xFF) * alpha;
                        blue += (argb & 0xFF) * alpha;
                    }
                }
                long weight = (long) (x1 - x0) * (y1 - y0) * 255;
                pixels[(y + offsetY) * size + x + offsetX] =
                        (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
            }
        }
        return pixels;
    }

    /** The {@code video_rgb} character nearest in colour to {@code rgb}. */
    static char pixel(int rgb) {
        return Palette.RGB.pixel(rgb);
    }

    static String frameLine(int[] pixels, Palette palette) {
        String open = palette.openTag();
        String close = palette.closeTag();
        StringBuilder line = new StringBuilder(open.length() + pixels.length + close.length());
        line.append(open);
        for (int rgb : pixels) {
            line.append(palette.pixel(rgb));
        }
        return line.append(close).toString();
    }

    // --- GIF frames ----------------------------------------------------------------------------

    private enum Disposal {
        KEEP, RESTORE_BACKGROUND, RESTORE_PREVIOUS;

        static Disposal of(String method) {
            return switch (method) {
                case "restoreToBackgroundColor" -> RESTORE_BACKGROUND;
                case "restoreToPrevious" -> RESTORE_PREVIOUS;
                default -> KEEP;
            };
        }
    }

    /** Where a frame is drawn on the canvas, for how long, and what happens to it after. */
    private record Frame(int left, int top, int width, int height, int delayCs, Disposal disposal) {

        static Frame of(IIOMetadata metadata, BufferedImage image) {
            Node root = root(metadata, "javax_imageio_gif_image_1.0");
            Node descriptor = child(root, "ImageDescriptor");
            Node control = child(root, "GraphicControlExtension");
            return new Frame(
                    descriptor == null ? 0 : intAttribute(descriptor, "imageLeftPosition", 0),
                    descriptor == null ? 0 : intAttribute(descriptor, "imageTopPosition", 0),
                    image.getWidth(), image.getHeight(),
                    delayCs(metadata),
                    control == null ? Disposal.KEEP : Disposal.of(attribute(control, "disposalMethod")));
        }

        /** How long the frame shows, in hundredths of a second. */
        static int delayCs(IIOMetadata metadata) {
            Node control = child(root(metadata, "javax_imageio_gif_image_1.0"), "GraphicControlExtension");
            int delay = control == null ? 0 : intAttribute(control, "delayTime", 0);
            return delay <= 1 ? DEFAULT_DELAY_CS : delay;
        }

        /** Undoes the frame as its disposal says, before the next one is drawn. */
        void dispose(BufferedImage canvas, int[] before) {
            switch (disposal) {
                case RESTORE_BACKGROUND -> {
                    // Browsers clear to transparent rather than to the background colour.
                    int x0 = Math.max(0, left);
                    int y0 = Math.max(0, top);
                    int x1 = Math.min(canvas.getWidth(), left + width);
                    int y1 = Math.min(canvas.getHeight(), top + height);
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            canvas.setRGB(x, y, 0);
                        }
                    }
                }
                case RESTORE_PREVIOUS -> {
                    if (before != null) {
                        canvas.setRGB(0, 0, canvas.getWidth(), canvas.getHeight(), before, 0, canvas.getWidth());
                    }
                }
                case KEEP -> {
                }
            }
        }
    }

    private static Node root(IIOMetadata metadata, String format) {
        if (metadata == null) {
            return null;
        }
        try {
            return metadata.getAsTree(format);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Node child(Node node, String name) {
        if (node == null) {
            return null;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    private static String attribute(Node node, String name) {
        Node attribute = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return attribute == null ? "" : attribute.getNodeValue();
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(attribute(node, name));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
