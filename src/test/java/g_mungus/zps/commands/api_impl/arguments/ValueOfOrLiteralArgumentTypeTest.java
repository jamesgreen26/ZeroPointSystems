package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValueOfOrLiteralArgumentType}.
 *
 * Tests the parse() method in isolation — no Minecraft game state required.
 */
public class ValueOfOrLiteralArgumentTypeTest {

    private static final ResourceLocation INT_KEY = ResourceLocation.parse("zps:int");
    private static final ResourceLocation BLOCK_POS_KEY = ResourceLocation.parse("zps:block_pos");

    private static ValueOfOrLiteralArgumentType<Integer> intType() {
        return ValueOfOrLiteralArgumentType.of(IntegerArgumentType.integer(), INT_KEY);
    }

    private static ValueOfOrLiteralArgumentType<?> blockPosType() {
        return ValueOfOrLiteralArgumentType.of(BlockPosArgument.blockPos(), BLOCK_POS_KEY);
    }

    @AfterEach
    void clearAddressContext() {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of());
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

    @Test
    public void parse_placeholder_returnsArgumentPlaceholder() throws CommandSyntaxException {
        StringReader reader = new StringReader("%s");
        Object result = intType().parse(reader);

        assertInstanceOf(ArgumentPlaceholder.class, result);
        assertEquals(INT_KEY, ((ArgumentPlaceholder) result).targetTypeKey());
        assertFalse(reader.canRead(), "Reader should be fully consumed");
    }

    @Test
    public void parse_placeholderWithTrailingText_onlyConsumesPlaceholder() throws CommandSyntaxException {
        StringReader reader = new StringReader("%s else");
        Object result = intType().parse(reader);

        assertInstanceOf(ArgumentPlaceholder.class, result);
        assertTrue(reader.canRead());
        assertEquals(' ', reader.peek());
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

    @Test
    public void parse_blockPosAddress_returnsAddressReference() throws CommandSyntaxException {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of("home"));
        StringReader reader = new StringReader("@home");
        Object result = blockPosType().parse(reader);
        assertInstanceOf(AddressReference.class, result);
        assertEquals("home", ((AddressReference) result).name());
    }

    @Test
    public void parse_blockPosAddress_notAvailableFallsBackToWrappedParser() {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of("home"));
        StringReader reader = new StringReader("@missing");
        assertThrows(CommandSyntaxException.class, () -> blockPosType().parse(reader));
    }

    @Test
    public void parse_nonBlockPosAddress_notAccepted() {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of("home"));
        StringReader reader = new StringReader("@home");
        assertThrows(CommandSyntaxException.class, () -> intType().parse(reader));
    }

    @Test
    public void suggest_blockPosAddresses_only() throws ExecutionException, InterruptedException {
        ValueOfOrLiteralArgumentType.setActiveAddressNames(Set.of("home", "hub"));
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<Object>();
        var context = new com.mojang.brigadier.context.CommandContextBuilder<>(dispatcher, new Object(), new RootCommandNode<>(), 0)
                .build("");
        var suggestions = blockPosType()
                .listSuggestions(context, new SuggestionsBuilder("@h", 0))
                .get();
        var texts = suggestions.getList().stream().map(s -> s.getText()).toList();
        assertTrue(texts.contains("@home"));
        assertTrue(texts.contains("@hub"));
    }
}
