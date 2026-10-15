package g_mungus.zps.blockentity.light_pipe;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A script plus the terminal settings from its header, as a {@code .mungus} file or {@code load_script} holds it.
 * The first line may be a settings header such as {@code # 4t IMPULSE}. {@code delay} and {@code repeat} are null
 * when there is no header.
 */
public record ScriptFile(String script, @Nullable Integer delay, @Nullable Boolean repeat) {
    private static final Pattern HEADER = Pattern.compile("#\\s*(\\d+)t\\s+(IMPULSE|REPEAT)\\s*", Pattern.CASE_INSENSITIVE);

    public String serialize() {
        if (this.delay == null || this.repeat == null) return this.script;
        return "# " + this.delay + "t " + (this.repeat ? "REPEAT" : "IMPULSE") + "\n" + this.script;
    }

    public static ScriptFile parse(String content) {
        content = content.replace("\r\n", "\n");
        int newline = content.indexOf('\n');
        String firstLine = newline < 0 ? content : content.substring(0, newline);
        Matcher header = HEADER.matcher(firstLine);
        if (!header.matches()) {
            return new ScriptFile(content, null, null);
        }
        String script = newline < 0 ? "" : content.substring(newline + 1);
        Integer delay;
        try {
            delay = Integer.parseInt(header.group(1));
        } catch (NumberFormatException e) {
            delay = null;
        }
        return new ScriptFile(script, delay, header.group(2).equalsIgnoreCase("REPEAT"));
    }
}
