package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.commands.api_impl.exceptions.ScriptCommandException;
import g_mungus.zps.commands.api_impl.exceptions.ValueOfEvaluationException;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Why a script command did not run, in words a player can act on, plus where in the command it
 * went wrong. Built from whatever the dispatcher threw and kept free of exception names and stack
 * traces, so it can be shown on a screen as-is.
 *
 * @param reason      what went wrong, one sentence
 * @param evaluated   the text the fault range refers to when that is not the player's own command
 *                    (a nested {@code value_of} expression), otherwise empty
 * @param faultStart  start of the offending span within the player's command, or within
 *                    {@code evaluated} when that is set; {@code -1} when unknown
 * @param faultEnd    end of that span, exclusive
 */
public record ScriptCommandFailure(String reason, String evaluated, int faultStart, int faultEnd) {

    private static final String UNKNOWN_REASON = "The command failed for an unknown reason";
    private static final String REASON_TAG = "Reason";
    private static final String EVALUATED_TAG = "Evaluated";
    private static final String START_TAG = "FaultStart";
    private static final String END_TAG = "FaultEnd";

    public static ScriptCommandFailure of(String reason) {
        return new ScriptCommandFailure(reason, "", -1, -1);
    }

    public boolean hasFault() {
        return faultStart >= 0 && faultEnd > faultStart;
    }

    /** Whether the fault range points into the player's own command rather than a nested expression. */
    public boolean faultInCommand() {
        return hasFault() && evaluated.isEmpty();
    }

    // --- building from an exception ---------------------------------------------------------

    /**
     * Describe a failure of {@code executed}, which is {@code playerCommand} with the dispatcher's
     * prefix in front of it. Positions are reported relative to the player's command.
     */
    public static ScriptCommandFailure describe(Throwable throwable, String playerCommand, String executed) {
        int prefixLength = Math.max(0, executed.length() - playerCommand.length());

        ValueOfEvaluationException valueOf = find(throwable, ValueOfEvaluationException.class);
        if (valueOf != null) {
            int start = playerCommand.indexOf(valueOf.token());
            if (start >= 0) {
                return new ScriptCommandFailure(valueOf.getMessage(), "", start, start + valueOf.token().length());
            }
        }

        ScriptCommandException located = find(throwable, ScriptCommandException.class);
        if (located != null) {
            return describeLocated(located, playerCommand, executed, prefixLength);
        }
        if (throwable instanceof CommandSyntaxException syntax) {
            return describeSyntax(syntax, playerCommand, executed, prefixLength);
        }
        return of(rootReason(throwable));
    }

    private static ScriptCommandFailure describeLocated(ScriptCommandException located, String playerCommand,
                                                        String executed, int prefixLength) {
        String reason = "Could not evaluate '" + located.commandPart() + "': " + rootReason(located.getCause());
        String input = located.input();
        StringRange range = located.range();
        if (input.equals(executed)) {
            int start = Math.max(0, range.getStart() - prefixLength);
            int end = Math.min(playerCommand.length(), range.getEnd() - prefixLength);
            return new ScriptCommandFailure(reason, "", start, end);
        }
        String shown = stripTerminal(input);
        int start = Math.max(0, Math.min(shown.length(), range.getStart()));
        int end = Math.max(start, Math.min(shown.length(), range.getEnd()));
        return new ScriptCommandFailure(reason, shown, start, end);
    }

    private static ScriptCommandFailure describeSyntax(CommandSyntaxException syntax, String playerCommand,
                                                       String executed, int prefixLength) {
        String raw = syntax.getRawMessage().getString();
        int cursor = syntax.getCursor();
        boolean atEnd = cursor < 0 || cursor >= executed.length();
        boolean inCommand = !atEnd && cursor >= prefixLength;

        int start = inCommand ? cursor - prefixLength : -1;
        int end = -1;
        String token = "";
        if (inCommand) {
            end = playerCommand.indexOf(' ', start);
            if (end < 0) end = playerCommand.length();
            if (end <= start) end = Math.min(playerCommand.length(), start + 1);
            token = playerCommand.substring(start, end);
        }

        // Brigadier's own wording is written for a chat window with a caret; put it in terms of
        // the command the player wrote instead.
        String reason;
        if (raw.startsWith("Unknown or incomplete command")) {
            reason = atEnd ? "The command is incomplete" : "Unknown command '" + token + "'";
        } else if (raw.startsWith("Incorrect argument for command")) {
            reason = start == 0
                    ? "Unknown command '" + token + "'"
                    : "'" + token + "' is not valid here";
        } else {
            reason = raw;
        }

        return inCommand ? new ScriptCommandFailure(reason, "", start, end) : of(reason);
    }

    private static <T extends Throwable> @Nullable T find(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private static String stripTerminal(String input) {
        String suffix = " " + ValueOfDispatchers.TERMINAL_LITERAL;
        return input.endsWith(suffix) ? input.substring(0, input.length() - suffix.length()) : input;
    }

    /**
     * The first message down the cause chain that says something on its own: wrappers that only
     * repeat their cause, or carry no message at all, are skipped. Never an exception's name.
     */
    public static String rootReason(@Nullable Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CommandSyntaxException syntax) {
                return syntax.getRawMessage().getString();
            }
            String message = current.getMessage();
            Throwable cause = current.getCause();
            if (message != null && !message.isBlank() && !repeatsCause(message, cause)) {
                return message;
            }
            current = cause;
        }
        return UNKNOWN_REASON;
    }

    private static boolean repeatsCause(String message, @Nullable Throwable cause) {
        return cause != null
                && (message.equals(cause.toString()) || message.startsWith(cause.getClass().getName()));
    }

    // --- persistence ------------------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.putString(REASON_TAG, reason);
        tag.putString(EVALUATED_TAG, evaluated);
        tag.putInt(START_TAG, faultStart);
        tag.putInt(END_TAG, faultEnd);
        return tag;
    }

    public static ScriptCommandFailure load(CompoundTag tag) {
        return new ScriptCommandFailure(tag.getString(REASON_TAG), tag.getString(EVALUATED_TAG),
                tag.getInt(START_TAG), tag.getInt(END_TAG));
    }
}
