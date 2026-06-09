package g_mungus.zps.commands.content.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RadioFrequencyArgument#parse}.
 *
 * Frequencies run from 05KHz to 80KHz in 5KHz steps and map to indices 0–15.
 */
public class RadioFrequencyArgumentTest {

    private static int parse(String input) throws CommandSyntaxException {
        return RadioFrequencyArgument.radioFrequency().parse(new StringReader(input));
    }

    @Test
    public void parse_firstFrequency_returnsZero() throws CommandSyntaxException {
        assertEquals(0, parse("05KHz"));
    }

    @Test
    public void parse_lastFrequency_returnsFifteen() throws CommandSyntaxException {
        assertEquals(15, parse("80KHz"));
    }

    @Test
    public void parse_middleFrequency_returnsExpectedIndex() throws CommandSyntaxException {
        assertEquals(7, parse("40KHz"));
    }

    @Test
    public void parse_invalidFrequency_throws() {
        assertThrows(CommandSyntaxException.class, () -> parse("99KHz"));
    }

    @Test
    public void parse_invalidFrequency_resetsCursorToTokenStart() {
        StringReader reader = new StringReader("99KHz");
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class,
                () -> RadioFrequencyArgument.radioFrequency().parse(reader));
        assertEquals(0, reader.getCursor(),
                "Cursor should be reset so the error points at the invalid token");
        assertEquals(0, e.getCursor());
    }

    @Test
    public void parse_onlyConsumesToken() throws CommandSyntaxException {
        StringReader reader = new StringReader("40KHz extra");
        assertEquals(7, RadioFrequencyArgument.radioFrequency().parse(reader));
        assertTrue(reader.canRead());
        assertEquals(' ', reader.peek());
    }

    @Test
    public void parse_isCaseSensitive() {
        // Documents current behaviour: suggestions match case-insensitively,
        // but parse only accepts the canonical "NNKHz" casing.
        assertThrows(CommandSyntaxException.class, () -> parse("40khz"));
    }

    @Test
    public void parse_allExamplesAreValid() {
        for (String example : RadioFrequencyArgument.radioFrequency().getExamples()) {
            assertDoesNotThrow(() -> parse(example), "Example should parse: " + example);
        }
    }
}
