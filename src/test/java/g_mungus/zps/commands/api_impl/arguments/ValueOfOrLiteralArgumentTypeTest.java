package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValueOfOrLiteralArgumentType}.
 *
 * Tests the parse() method in isolation — no Minecraft game state required.
 */
public class ValueOfOrLiteralArgumentTypeTest {

    private static final ResourceLocation INT_KEY = ResourceLocation.parse("zps:int");

    private static ValueOfOrLiteralArgumentType<Integer> intType() {
        return ValueOfOrLiteralArgumentType.of(IntegerArgumentType.integer(), INT_KEY);
    }

    // -------------------------------------------------------------------------
    // Literal passthrough
    // -------------------------------------------------------------------------

    @Test
    public void parse_literal_delegatesToWrappedType() throws CommandSyntaxException {
        StringReader reader = new StringReader("42");
        Object result = intType().parse(reader);
        assertEquals(42, result, "Literal integer should be parsed by wrapped IntegerArgumentType");
        assertFalse(reader.canRead(), "Reader should be fully consumed");
    }

    @Test
    public void parse_negativeLiteral_delegatesToWrappedType() throws CommandSyntaxException {
        StringReader reader = new StringReader("-7");
        Object result = intType().parse(reader);
        assertEquals(-7, result);
    }

    // -------------------------------------------------------------------------
    // value_of detection
    // -------------------------------------------------------------------------

    @Test
    public void parse_simpleValueOf_returnsValueOfExpression() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(pos x)");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result, "Should return a ValueOfExpression");
        ValueOfExpression<?> expr = (ValueOfExpression<?>) result;
        assertEquals("pos x", expr.innerExpression());
        assertEquals(INT_KEY, expr.targetTypeKey());
        assertFalse(reader.canRead(), "Reader should be fully consumed after closing )");
    }

    @Test
    public void parse_nestedValueOf_capturesFullInnerExpression() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(pos x + value_of(pos y))");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        ValueOfExpression<?> expr = (ValueOfExpression<?>) result;
        assertEquals("pos x + value_of(pos y)", expr.innerExpression(),
                "Nested value_of( ) should be included in the inner expression string");
        assertFalse(reader.canRead());
    }

    @Test
    public void parse_valueOfWithArithmetic_capturesInnerExpression() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(pos x + 3)");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        assertEquals("pos x + 3", ((ValueOfExpression<?>) result).innerExpression());
    }

    @Test
    public void parse_valueOfWithQuotedParen_capturesInnerExpression() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(dimension as_string + \"(\")");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        assertEquals("dimension as_string + \"(\"", ((ValueOfExpression<?>) result).innerExpression());
        assertFalse(reader.canRead(), "Reader should stop at the outer closing parenthesis");
    }

    @Test
    public void parse_valueOfWithEscapedQuoteAndParen_capturesInnerExpression() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(read_page + \"\\\\\\\")\" )");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        assertEquals("read_page + \"\\\\\\\")\" ", ((ValueOfExpression<?>) result).innerExpression());
        assertFalse(reader.canRead(), "Reader should ignore parentheses inside escaped quoted text");
    }

    @Test
    public void parse_valueOfEmpty_returnsExpressionWithEmptyInner() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of()");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        assertEquals("", ((ValueOfExpression<?>) result).innerExpression());
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    public void parse_unclosedParen_throwsException() {
        StringReader reader = new StringReader("value_of(pos x");
        assertThrows(CommandSyntaxException.class, () -> intType().parse(reader),
                "Unclosed parenthesis should throw CommandSyntaxException");
    }

    // -------------------------------------------------------------------------
    // Reader cursor behaviour
    // -------------------------------------------------------------------------

    @Test
    public void parse_literalWithTrailingText_onlyConsumesLiteralPart() throws CommandSyntaxException {
        // Brigadier parsers only read what they need; remaining text stays in reader
        StringReader reader = new StringReader("5 extra");
        Object result = intType().parse(reader);
        assertEquals(5, result);
        // The space and "extra" should still be in the reader
        assertTrue(reader.canRead());
        assertEquals(' ', reader.peek());
    }

    @Test
    public void parse_valueOfWithTrailingText_onlyConsumesUpToClosingParen() throws CommandSyntaxException {
        StringReader reader = new StringReader("value_of(pos x) more");
        Object result = intType().parse(reader);

        assertInstanceOf(ValueOfExpression.class, result);
        assertEquals("pos x", ((ValueOfExpression<?>) result).innerExpression());
        // " more" should remain
        assertTrue(reader.canRead());
        assertEquals(' ', reader.peek());
    }
}
