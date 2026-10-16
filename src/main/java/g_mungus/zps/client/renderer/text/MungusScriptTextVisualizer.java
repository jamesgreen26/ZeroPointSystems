package g_mungus.zps.client.renderer.text;

import g_mungus.munguscript.engine.ScriptView;
import g_mungus.zps.client.script.ClientScripts;
import g_mungus.zps.client.screens.components.ScriptLineHighlighter;
import g_mungus.zps.client.screens.components.ScriptSyntaxHighlighter;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Script text coloured as the script terminal colours it, line by line, glyphs as written. Plain
 * words take the terminal's grey rather than the dye.
 *
 * <p>A display cannot know what a terminal would know, so what it cannot check it takes as
 * valid: every {@code @name} is taken for an address the terminal's Address Pad holds, and every
 * {@code receive_value} for a variable the script declares.
 */
public final class MungusScriptTextVisualizer implements TextVisualizer {

    private static final int CACHE_SIZE = 64;
    private static final int FALLBACK_COLOR = color(ScriptSyntaxHighlighter.DEFAULT_STYLE);
    /** An {@code @name} as {@code AddressPreProcessor} reads one, with any name an Address Pad allows. */
    private static final Pattern ADDRESS = Pattern.compile("(?<![A-Za-z0-9._])@([A-Za-z0-9._]+)");

    /** Colours by script text, since working them out costs a parse; dropped when the scripts change. */
    private final Map<String, int[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private @Nullable ScriptView<SharedSuggestionProvider> cachedView;

    @Override
    public List<VisualGlyph> visualize(String text, DyeColor dye) {
        int[] colors = colors(text);
        List<VisualGlyph> glyphs = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            glyphs.add(new VisualGlyph(text.charAt(i), colors[i]));
        }
        return glyphs;
    }

    private int[] colors(String text) {
        ScriptView<SharedSuggestionProvider> view = ClientScripts.view();
        if (view != cachedView) {
            cache.clear();
            cachedView = view;
        }
        int[] cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        int[] colors = new int[text.length()];
        Arrays.fill(colors, FALLBACK_COLOR);
        if (view == null || Minecraft.getInstance().getConnection() == null) {
            // Not kept, so the text colours once the scripts arrive.
            return colors;
        }
        try {
            highlight(view, text, colors);
        } catch (RuntimeException e) {
            Arrays.fill(colors, FALLBACK_COLOR);
        }
        cache.put(text, colors);
        return colors;
    }

    /** Colours each line as a script terminal line, every line a command. */
    private static void highlight(ScriptView<SharedSuggestionProvider> view, String text, int[] colors) {
        ScriptLineHighlighter highlighter = new ScriptLineHighlighter(true, null);
        highlighter.setAssumeVariablesDeclared(true);
        Map<String, BlockPos> addresses = assumedAddresses(text);
        String[] lines = text.split("\n", -1);
        int lineStart = 0;
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];
            List<ScriptSyntaxHighlighter.Span> spans = ScriptSyntaxHighlighter.flatten(line,
                    highlighter.lineSpans(view, text, addresses, line, lineNumber));
            for (ScriptSyntaxHighlighter.Span span : spans) {
                int color = color(span.style());
                for (int i = span.start(); i < span.end(); i++) {
                    colors[lineStart + i] = color;
                }
            }
            lineStart += line.length() + 1;
        }
    }

    /** Every {@code @name} in the text, each standing for some block; where does not change the colours. */
    private static Map<String, BlockPos> assumedAddresses(String text) {
        Map<String, BlockPos> addresses = new HashMap<>();
        Matcher matcher = ADDRESS.matcher(text);
        while (matcher.find()) {
            addresses.put(matcher.group(1), BlockPos.ZERO);
        }
        return addresses;
    }

    private static int color(Style style) {
        TextColor color = style.getColor();
        return color == null ? 0xFFFFFF : color.getValue();
    }
}
