package g_mungus.zps.client.video;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import g_mungus.zps.client.screens.ScriptFileScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /zps_client convert_gif <url> <name> [rgb|hue]}: downloads a GIF, MP4, PNG or JPEG and
 * saves it as {@code <name>.mungus} in the scripts folder, as {@code video_rgb} frames (the default)
 * or {@code video_hue} frames for a 3x3 text display. A name with spaces goes in quotes. See
 * {@link ImageVideoConverter}. The download and conversion run off the game thread; the result is
 * reported in chat.
 */
public final class ImageScriptCommand {
    private static final String ARG_URL = "url";
    private static final String ARG_NAME = "name";
    private static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_client")
                    .then(Commands.literal("convert_gif")
                            .then(Commands.argument(ARG_URL, UrlArgument.INSTANCE)
                                    .then(Commands.argument(ARG_NAME, StringArgumentType.string())
                                            .executes(context -> run(context, ImageVideoConverter.Palette.RGB))
                                            .then(Commands.literal("rgb")
                                                    .executes(context -> run(context, ImageVideoConverter.Palette.RGB)))
                                            .then(Commands.literal("hue")
                                                    .executes(context -> run(context, ImageVideoConverter.Palette.HUE))))));

    private ImageScriptCommand() {
    }

    private static int run(CommandContext<CommandSourceStack> context, ImageVideoConverter.Palette palette) {
        URI url = context.getArgument(ARG_URL, URI.class);
        String name = StringArgumentType.getString(context, ARG_NAME);
        Path target = ScriptFileScreen.scriptPath(name);
        if (target == null) {
            context.getSource().sendFailure(Component.literal("Invalid file name: " + name));
            return 0;
        }
        if (Files.exists(target)) {
            context.getSource().sendFailure(Component.literal(target.getFileName()
                    + " already exists; pick another name or delete it first"));
            return 0;
        }

        report(Component.literal("Downloading " + url + "...").withStyle(ChatFormatting.GRAY));
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .header("User-Agent", "ZeroPointSystems")
                .GET()
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> {
                    try (InputStream body = response.body()) {
                        if (response.statusCode() / 100 != 2) {
                            throw new IOException("The server answered " + response.statusCode());
                        }
                        return ImageVideoConverter.convert(new LimitedInputStream(body, MAX_DOWNLOAD_BYTES), palette);
                    } catch (IOException e) {
                        throw new ConversionException(e.getMessage(), e);
                    }
                }, Util.backgroundExecutor())
                .thenAcceptAsync(result -> save(target, result), Util.ioPool())
                .exceptionally(failure -> {
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    ZPSMod.LOGGER.warn("Could not turn {} into a script", url, cause);
                    report(Component.literal("Could not convert the image: " + cause.getMessage()).withStyle(ChatFormatting.RED));
                    return null;
                });
        return 1;
    }

    private static void save(Path target, ImageVideoConverter.Result result) {
        if (result.script().length() > ScriptTerminalBlockEntity.MAX_SCRIPT_LENGTH) {
            throw new ConversionException("The frames came to " + result.script().length()
                    + " characters, more than a terminal holds", null);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, result.script(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConversionException("Could not save " + target.getFileName() + ": " + e.getMessage(), e);
        }
        String saved = "Saved " + target.getFileName() + ": ";
        if (result.ticksPerFrame() == 0) {
            report(Component.literal(saved + "one frame from the " + result.format() + ".")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        String speed = String.format(Locale.ROOT, "%.1f", result.ticksPerFrame());
        report(Component.literal(saved + result.frames() + " of " + result.sourceFrames()
                + " frames. For the " + result.format() + "'s speed, show each frame for about " + speed + " ticks.")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Puts a line in chat from any thread. */
    private static void report(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
        });
    }

    private static final class ConversionException extends RuntimeException {
        ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Fails a read once more than {@code limit} bytes have come through, so a huge download stops early. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long read;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws IOException {
            read += n;
            if (read > limit) {
                throw new IOException("The download is larger than " + limit / (1024 * 1024) + " MB");
            }
        }
    }

    /** An http or https URL, read up to the next space so it needs no quotes. */
    private static final class UrlArgument implements ArgumentType<URI> {
        static final UrlArgument INSTANCE = new UrlArgument();
        private static final SimpleCommandExceptionType INVALID =
                new SimpleCommandExceptionType(Component.literal("Expected an http or https URL"));

        @Override
        public URI parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            String text = reader.getString().substring(start, reader.getCursor());
            try {
                URI uri = new URI(text);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                    return uri;
                }
            } catch (URISyntaxException ignored) {
                // Reported below, at the start of the URL.
            }
            reader.setCursor(start);
            throw INVALID.createWithContext(reader);
        }
    }
}
