package g_mungus.zps.commands.content;

import org.jetbrains.annotations.Nullable;

/**
 * The lines only a Script Terminal runs itself, {@code wait <cycles>} and {@code receive <name>},
 * read the same way by the terminal and by the script editor. Any run of whitespace separates the
 * keyword from its argument.
 *
 * <p>Also the form a Serial Bus's answer takes on the cable, {@code provide_value <value>}: every
 * receiver gets it like any other text, and the prefix is what tells a bus not to run it.
 */
public final class TerminalLines {
    public static final String WAIT = "wait";
    public static final String RECEIVE = "receive";
    public static final String PROVIDE_VALUE = "provide_value";
    /** The most cycles one {@code wait} may ask for. */
    public static final int MAX_WAIT_CYCLES = 64;

    private TerminalLines() {
    }

    /**
     * The argument after {@code keyword} when {@code line} is that keyword's line, stripped and
     * possibly empty; null when the line is anything else.
     */
    public static @Nullable String argument(String line, String keyword) {
        String stripped = line.strip();
        if (!stripped.startsWith(keyword)) {
            return null;
        }
        if (stripped.length() == keyword.length()) {
            return "";
        }
        return Character.isWhitespace(stripped.charAt(keyword.length()))
                ? stripped.substring(keyword.length()).strip()
                : null;
    }

    /** {@code value} as an answer goes on the cable. */
    public static String provideValue(String value) {
        return PROVIDE_VALUE + " " + value;
    }

    /** The value {@code message} provides, exactly as it was sent, or null if it is not an answer. */
    public static @Nullable String providedValue(String message) {
        String prefix = PROVIDE_VALUE + " ";
        return message.startsWith(prefix) ? message.substring(prefix.length()) : null;
    }

    public static boolean isWait(String line) {
        return argument(line, WAIT) != null;
    }

    public static boolean isReceive(String line) {
        return argument(line, RECEIVE) != null;
    }

    /**
     * The cycles a {@code wait} line asks for, or null if its argument is not a whole number from 1
     * to {@link #MAX_WAIT_CYCLES}.
     */
    public static @Nullable Integer waitCycles(String line) {
        String argument = argument(line, WAIT);
        if (argument == null) {
            return null;
        }
        try {
            int cycles = Integer.parseInt(argument);
            return cycles >= 1 && cycles <= MAX_WAIT_CYCLES ? cycles : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
