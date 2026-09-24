package g_mungus.zps.commands.api_impl.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * A {@code value_of(...)} expression that could not be read as a value of the type its command
 * needs. The message is already written for players: which type the expression yields, which
 * type was wanted, and what could follow to bridge the two.
 */
public class ValueOfEvaluationException extends RuntimeException {
    private final String expression;

    public ValueOfEvaluationException(String expression, String reason, @NotNull Throwable cause) {
        super(reason, cause);
        this.expression = expression;
    }

    /** The text between the parentheses, as the player wrote it. */
    public String expression() {
        return expression;
    }

    /** The whole token as it appears in the command. */
    public String token() {
        return "value_of(" + expression + ")";
    }
}
