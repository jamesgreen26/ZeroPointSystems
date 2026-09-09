package g_mungus.zps.commands.api_impl.exceptions;

import com.mojang.brigadier.context.StringRange;
import org.jetbrains.annotations.NotNull;

/**
 * A failure inside a script command's mapper or argument evaluation, carrying where it happened so
 * whoever ran the command can point at the offending part without a stack trace.
 *
 * <p>{@link #input()} is the command text being evaluated at the time, which is the whole command
 * for a plain mapper and the inner expression for a nested {@code value_of}. {@link #range()} is
 * the span within that text of the node that failed.
 */
public class ScriptCommandException extends RuntimeException {
    private final String phase;
    private final String commandPart;
    private final String input;
    private final StringRange range;
    private final boolean logged;

    public ScriptCommandException(String phase, String commandPart, String input, StringRange range,
                                  boolean logged, @NotNull Throwable cause) {
        super(cause.getMessage(), cause);
        this.phase = phase;
        this.commandPart = commandPart;
        this.input = input;
        this.range = range;
        this.logged = logged;
    }

    /** What was being evaluated: a "mapper", "mapper argument", or the value_of equivalents. */
    public String phase() {
        return phase;
    }

    /** The mapper's display name, as the player wrote it. */
    public String commandPart() {
        return commandPart;
    }

    public String input() {
        return input;
    }

    public StringRange range() {
        return range;
    }

    /** Whether the failure was already written to the log when it was raised. */
    public boolean logged() {
        return logged;
    }
}
