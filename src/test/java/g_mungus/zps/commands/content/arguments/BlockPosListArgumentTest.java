package g_mungus.zps.commands.content.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockPosListArgument#parse}.
 *
 * Coordinates are compared against the result of parsing the same coordinate
 * text with the vanilla {@link BlockPosArgument}; coordinate resolution against
 * a command source is covered by game tests.
 */
public class BlockPosListArgumentTest {

    private static List<Coordinates> parse(String input) throws CommandSyntaxException {
        return BlockPosListArgument.blockPosList().parse(new StringReader(input));
    }

    private static Coordinates single(String input) throws CommandSyntaxException {
        return BlockPosArgument.blockPos().parse(new StringReader(input));
    }

    @Test
    public void parse_singleAbsoluteEntry() throws CommandSyntaxException {
        List<Coordinates> result = parse("[0 64 0]");
        assertEquals(List.of(single("0 64 0")), result);
    }

    @Test
    public void parse_multipleEntries() throws CommandSyntaxException {
        List<Coordinates> result = parse("[0 64 0, 10 70 -5]");
        assertEquals(List.of(single("0 64 0"), single("10 70 -5")), result);
    }

    @Test
    public void parse_commaWithoutSpace() throws CommandSyntaxException {
        List<Coordinates> result = parse("[1 2 3,4 5 6]");
        assertEquals(List.of(single("1 2 3"), single("4 5 6")), result);
    }

    @Test
    public void parse_relativeAndLocalEntries() throws CommandSyntaxException {
        List<Coordinates> result = parse("[~ ~1 ~, ^1 ^ ^-3]");
        assertEquals(List.of(single("~ ~1 ~"), single("^1 ^ ^-3")), result);
    }

    @Test
    public void parse_emptyList() throws CommandSyntaxException {
        assertTrue(parse("[]").isEmpty());
        assertTrue(parse("[ ]").isEmpty());
    }

    @Test
    public void parse_onlyConsumesUpToClosingBracket() throws CommandSyntaxException {
        StringReader reader = new StringReader("[1 2 3] trailing");
        List<Coordinates> result = BlockPosListArgument.blockPosList().parse(reader);
        assertEquals(1, result.size());
        assertTrue(reader.canRead());
        assertEquals(' ', reader.peek(), "Parsing should stop right after ']'");
    }

    @Test
    public void parse_missingOpenBracket_throws() {
        assertThrows(CommandSyntaxException.class, () -> parse("1 2 3"));
    }

    @Test
    public void parse_missingCloseBracket_throws() {
        assertThrows(CommandSyntaxException.class, () -> parse("[1 2 3"));
    }

    @Test
    public void parse_invalidSeparator_throws() {
        assertThrows(CommandSyntaxException.class, () -> parse("[1 2 3; 4 5 6]"));
    }

    @Test
    public void parse_incompleteCoordinate_throws() {
        assertThrows(CommandSyntaxException.class, () -> parse("[1 2]"));
    }

    @Test
    public void parse_allExamplesAreValid() {
        for (String example : BlockPosListArgument.blockPosList().getExamples()) {
            assertDoesNotThrow(() -> parse(example), "Example should parse: " + example);
        }
    }
}
