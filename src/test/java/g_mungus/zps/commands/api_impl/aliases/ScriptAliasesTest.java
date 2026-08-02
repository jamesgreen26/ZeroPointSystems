package g_mungus.zps.commands.api_impl.aliases;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptAliasesTest {
    private static final Set<String> MAPPERS = Set.of("get_line", "+", "lines");
    private static final Set<String> GETTERS = Set.of("read_page", "redstone");

    private static ScriptAliases.ParsedScript parse(String script) {
        return ScriptAliases.parse(script, MAPPERS::contains, GETTERS::contains);
    }

    @Test
    public void parse_leadingDefinitionsRemovedFromCommands() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def line_1 = read_page get_line 1
                #def line_2 = read_page get_line 2

                write_page value_of(line_1)
                """);

        assertEquals(2, parsed.definitions().size());
        assertEquals(1, parsed.commands().size());
        assertEquals("write_page value_of(line_1)", parsed.commands().getFirst());
        assertTrue(parsed.diagnostics().isEmpty());
    }

    @Test
    public void parse_blankLinesIgnoredAndFirstNonHashEndsDeclarationBlock() {
        ScriptAliases.ParsedScript parsed = parse("""

                #def first = read_page get_line 1
                #default comment

                write_page value_of(first)
                #def late = read_page get_line 2
                """);

        assertEquals(1, parsed.definitions().size());
        assertEquals(2, parsed.commands().size());
        assertEquals("#def late = read_page get_line 2", parsed.commands().get(1));
        assertTrue(parsed.diagnostics().isEmpty());
    }

    @Test
    public void parse_mapperNameCollisionIsDiagnostic() {
        ScriptAliases.ParsedScript parsed = parse("#def get_line = read_page");

        assertTrue(parsed.aliases().isEmpty());
        assertEquals(1, parsed.diagnostics().size());
        assertTrue(parsed.diagnostics().getFirst().message().contains("conflicts"));
    }

    @Test
    public void parse_mapperOnlyAliasBodyIsDiagnostic() {
        ScriptAliases.ParsedScript parsed = parse("#def first_line = get_line 1");

        assertTrue(parsed.aliases().isEmpty());
        assertEquals(1, parsed.diagnostics().size());
        assertTrue(parsed.diagnostics().getFirst().message().contains("must start"));
    }

    @Test
    public void parse_duplicateAliasUsesMostRecentForFollowingCommands() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def page = read_page get_line 1
                #def page = read_page get_line 2
                write_page value_of(page)
                """);

        assertEquals("read_page get_line 2", parsed.aliases().get("page").expression());
        assertEquals(2, parsed.definitions().size());
    }

    @Test
    public void resolveExpression_aliasCanReferencePreviousAlias() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def line_1 = read_page get_line 1
                #def line_count = line_1 lines
                write_page value_of(line_count)
                """);

        String resolved = ScriptAliases.resolveExpression("line_count", parsed.aliases());

        assertEquals("read_page get_line 1 lines", resolved);
    }

    @Test
    public void resolveExpression_forwardReferenceDoesNotResolve() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def first = second lines
                #def second = read_page
                write_page value_of(first)
                """);

        assertFalse(parsed.aliases().containsKey("first"));
        assertTrue(parsed.aliases().containsKey("second"));
        assertEquals(1, parsed.diagnostics().size());
    }

    @Test
    public void resolveExpression_aliasDoesNotExpandInMapperPosition() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def first_line = read_page get_line 1
                write_page value_of(read_page first_line)
                """);

        String resolved = ScriptAliases.resolveExpression("read_page first_line", parsed.aliases());

        assertEquals("read_page first_line", resolved);
    }

    @Test
    public void resolveExpression_aliasExpandsAtExpressionRoot() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def first_line = read_page get_line 1
                write_page value_of(first_line)
                """);

        String resolved = ScriptAliases.resolveExpression("first_line", parsed.aliases());

        assertEquals("read_page get_line 1", resolved);
    }

    @Test
    public void resolveExpression_getterAliasDoesNotExpandAsLiteralMapperArgument() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def expected = read_page get_line 1
                write_page value_of(read_page + expected)
                """);

        String resolved = ScriptAliases.resolveExpression("read_page + expected", parsed.aliases());

        assertEquals("read_page + expected", resolved);
    }

    @Test
    public void resolveExpression_quotedAliasNameIsNotExpanded() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def page = read_page
                write_page value_of(page + "page")
                """);

        String resolved = ScriptAliases.resolveExpression("page + \"page\"", parsed.aliases());

        assertEquals("read_page + \"page\"", resolved);
    }

    @Test
    public void resolveExpression_combinedAliasExpandsAtExpressionRoot() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def line_1 = read_page get_line 1
                #def line_2 = read_page get_line 2
                #def combined = line_2 + "\\n" + value_of(line_1)
                write_page value_of(combined)
                """);

        String resolved = ScriptAliases.resolveExpression("combined", parsed.aliases());

        assertEquals("read_page get_line 2 + \"\\n\" + value_of(read_page get_line 1)", resolved);
    }

    @Test
    public void resolveValueOfExpressions_resolvesNestedValueOfBodies() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def line_1 = read_page get_line 1
                #def line_2 = read_page get_line 2
                #def combined = line_2 + "\\n" + value_of(line_1)
                write_page value_of(combined)
                """);

        String resolved = ScriptAliases.resolveValueOfExpressions(
                "write_page value_of(combined)",
                parsed.aliases()
        );

        assertEquals("write_page value_of(read_page get_line 2 + \"\\n\" + value_of(read_page get_line 1))", resolved);
    }

    @Test
    public void resolveValueOfExpressions_preservesMappersAfterAlias() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def page = read_page
                write_page value_of(page get_line 1)
                """);

        String resolved = ScriptAliases.resolveValueOfExpressions(
                "write_page value_of(page get_line 1)",
                parsed.aliases()
        );

        assertEquals("write_page value_of(read_page get_line 1)", resolved);
    }

    @Test
    public void resolveCommandExpressions_resolvesConditionalAlias() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def high = redstone lines
                if high set_redstone 15
                """);

        String resolved = ScriptAliases.resolveCommandExpressions(
                "if high set_redstone 15",
                parsed.aliases(),
                expression -> expression.equals("redstone lines")
        );

        assertEquals("if redstone lines set_redstone 15", resolved);
    }

    @Test
    public void resolveCommandExpressions_preservesMappersAfterConditionalAlias() {
        ScriptAliases.ParsedScript parsed = parse("""
                #def signal = redstone
                if signal > 7 set_redstone 0
                """);

        String resolved = ScriptAliases.resolveCommandExpressions(
                "if signal > 7 set_redstone 0",
                parsed.aliases(),
                expression -> expression.equals("redstone > 7")
        );

        assertEquals("if redstone > 7 set_redstone 0", resolved);
    }
}
