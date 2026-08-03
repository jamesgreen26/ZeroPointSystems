package g_mungus.zps.commands.api_impl.aliases;

import g_mungus.zps.commands.api_impl.ZPSCommands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptAliases {
    private static final Pattern DEFINITION_PATTERN =
            Pattern.compile("^#def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");
    private static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String VALUE_OF_PREFIX = "value_of(";
    private static final int MAX_EXPANSION_DEPTH = 64;

    private ScriptAliases() {
    }

    public static ParsedScript parse(String script) {
        return parse(script, ZPSCommands::isMapperName, ZPSCommands::isGetterName);
    }

    public static ParsedScript parse(String script, Predicate<String> reservedAliasName, Predicate<String> getterName) {
        List<AliasDefinition> definitions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, AliasDefinition> aliases = new LinkedHashMap<>();
        String[] lines = script.split("\n", -1);
        boolean inDeclarationBlock = true;

        for (int i = 0; i < lines.length; i++) {
            String line = stripTrailingCarriageReturn(lines[i]);
            String trimmed = line.strip();
            if (trimmed.isBlank()) {
                continue;
            }

            if (inDeclarationBlock && trimmed.startsWith("#")) {
                if (startsWithDefinitionKeyword(trimmed)) {
                    parseDefinition(trimmed, i, aliases, definitions, diagnostics, reservedAliasName, getterName);
                }
                continue;
            }

            inDeclarationBlock = false;
            commands.add(line);
        }

        return new ParsedScript(List.copyOf(definitions), Map.copyOf(aliases), List.copyOf(commands), List.copyOf(diagnostics));
    }

    private static void parseDefinition(
            String line,
            int lineIndex,
            Map<String, AliasDefinition> aliases,
            List<AliasDefinition> definitions,
            List<Diagnostic> diagnostics,
            Predicate<String> reservedAliasName,
            Predicate<String> getterName
    ) {
        Matcher matcher = DEFINITION_PATTERN.matcher(line);
        if (!matcher.matches()) {
            diagnostics.add(new Diagnostic(lineIndex, "Invalid alias declaration"));
            return;
        }

        String name = matcher.group(1);
        String expression = matcher.group(2).strip();
        if (expression.isEmpty()) {
            diagnostics.add(new Diagnostic(lineIndex, "Alias expression cannot be empty"));
            return;
        }
        if (reservedAliasName.test(name)) {
            diagnostics.add(new Diagnostic(lineIndex, "Alias name '" + name + "' conflicts with a mapper"));
            return;
        }

        String firstToken = firstExpressionToken(expression);
        if (firstToken == null || (!getterName.test(firstToken) && !aliases.containsKey(firstToken))) {
            diagnostics.add(new Diagnostic(lineIndex, "Alias expression must start with a getter or previous alias"));
            return;
        }

        AliasDefinition definition = new AliasDefinition(name, expression, Map.copyOf(aliases), lineIndex);
        definitions.add(definition);
        aliases.put(name, definition);
    }

    public static String resolveCommandExpressions(String command, Map<String, AliasDefinition> aliases, Predicate<String> booleanExpressionParser) {
        String valueResolved = resolveValueOfExpressions(command, aliases);
        return resolveConditionalExpression(valueResolved, aliases, booleanExpressionParser);
    }

    public static String resolveExpression(String expression, Map<String, AliasDefinition> aliases) {
        String current = expression;
        for (int depth = 0; depth < MAX_EXPANSION_DEPTH; depth++) {
            String next = resolveExpressionOnce(current, aliases);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        throw new IllegalArgumentException("Alias expansion exceeded " + MAX_EXPANSION_DEPTH + " passes");
    }

    public static String resolveValueOfExpressions(String text, Map<String, AliasDefinition> aliases) {
        StringBuilder out = new StringBuilder();
        boolean inQuotedString = false;
        boolean escaping = false;
        int cursor = 0;

        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (inQuotedString) {
                out.append(c);
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inQuotedString = false;
                }
                cursor++;
                continue;
            }

            if (c == '"') {
                inQuotedString = true;
                out.append(c);
                cursor++;
                continue;
            }

            if (startsValueOfAt(text, cursor)) {
                int close = findClosingParen(text, cursor + VALUE_OF_PREFIX.length() - 1);
                if (close != -1) {
                    String inner = text.substring(cursor + VALUE_OF_PREFIX.length(), close);
                    out.append(VALUE_OF_PREFIX)
                            .append(resolveExpression(inner, aliases))
                            .append(')');
                    cursor = close + 1;
                    continue;
                }
            }

            out.append(c);
            cursor++;
        }

        return out.toString();
    }

    private static String resolveExpressionOnce(
            String expression,
            Map<String, AliasDefinition> aliases
    ) {
        String valueResolved = resolveValueOfExpressions(expression, aliases);
        Token token = firstRootAliasToken(valueResolved);
        if (token == null) {
            return valueResolved;
        }

        AliasDefinition alias = aliases.get(token.text());
        if (alias == null) {
            return valueResolved;
        }

        String replacement = resolveExpression(alias.expression(), alias.visibleAliases());
        return valueResolved.substring(0, token.start())
                + replacement
                + valueResolved.substring(token.end());
    }

    private static String resolveConditionalExpression(
            String command,
            Map<String, AliasDefinition> aliases,
            Predicate<String> booleanExpressionParser
    ) {
        String stripped = command.stripLeading();
        int leadingWhitespace = command.length() - stripped.length();
        String keyword;
        if (startsWithKeyword(stripped, "if")) {
            keyword = "if";
        } else if (startsWithKeyword(stripped, "unless")) {
            keyword = "unless";
        } else {
            return command;
        }

        int expressionStart = leadingWhitespace + keyword.length();
        if (expressionStart >= command.length() || !Character.isWhitespace(command.charAt(expressionStart))) {
            return command;
        }
        while (expressionStart < command.length() && Character.isWhitespace(command.charAt(expressionStart))) {
            expressionStart++;
        }

        List<Integer> boundaries = whitespaceBoundaries(command, expressionStart);
        for (int boundary : boundaries) {
            String candidate = command.substring(expressionStart, boundary);
            String resolvedCandidate = resolveExpression(candidate, aliases);
            if (booleanExpressionParser.test(resolvedCandidate)) {
                return command.substring(0, expressionStart)
                        + resolvedCandidate
                        + command.substring(boundary);
            }
        }

        return command;
    }

    private static List<Integer> whitespaceBoundaries(String text, int start) {
        List<Integer> out = new ArrayList<>();
        boolean inQuotedString = false;
        boolean escaping = false;
        int parenDepth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotedString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inQuotedString = false;
                }
                continue;
            }

            if (c == '"') {
                inQuotedString = true;
            } else if (c == '(') {
                parenDepth++;
            } else if (c == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (Character.isWhitespace(c) && parenDepth == 0) {
                out.add(i);
            }
        }
        out.add(text.length());
        return out;
    }

    private static boolean startsWithKeyword(String text, String keyword) {
        return text.equals(keyword) || text.startsWith(keyword + " ");
    }

    public static boolean startsWithDefinitionKeyword(String text) {
        String stripped = text.stripLeading();
        return stripped.equals("#def") || stripped.startsWith("#def ");
    }

    private static List<Token> expressionTokens(String text) {
        List<Token> out = new ArrayList<>();
        boolean inQuotedString = false;
        boolean escaping = false;
        int cursor = 0;

        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (inQuotedString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inQuotedString = false;
                }
                cursor++;
                continue;
            }

            if (c == '"') {
                inQuotedString = true;
                cursor++;
                continue;
            }

            if (startsValueOfAt(text, cursor)) {
                int close = findClosingParen(text, cursor + VALUE_OF_PREFIX.length() - 1);
                if (close != -1) {
                    out.add(new Token(text.substring(cursor, close + 1), cursor, close + 1));
                    cursor = close + 1;
                    continue;
                }
            }

            if (Character.isWhitespace(c)) {
                cursor++;
            } else {
                int start = cursor;
                while (cursor < text.length()
                        && !Character.isWhitespace(text.charAt(cursor))
                        && text.charAt(cursor) != '"') {
                    cursor++;
                }
                out.add(new Token(text.substring(start, cursor), start, cursor));
            }
        }
        return out;
    }

    private static String firstExpressionToken(String text) {
        List<Token> tokens = expressionTokens(text);
        return tokens.isEmpty() ? null : tokens.get(0).text();
    }

    private static Token firstRootAliasToken(String text) {
        Matcher matcher = ALIAS_NAME_PATTERN.matcher(text);
        int cursor = 0;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        matcher.region(cursor, text.length());
        if (!matcher.lookingAt()) {
            return null;
        }
        int end = matcher.end();
        if (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            return null;
        }
        return new Token(matcher.group(), matcher.start(), matcher.end());
    }

    private static boolean startsValueOfAt(String text, int cursor) {
        return text.startsWith(VALUE_OF_PREFIX, cursor);
    }

    private static int findClosingParen(String text, int openParen) {
        int depth = 0;
        boolean inQuotedString = false;
        boolean escaping = false;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotedString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inQuotedString = false;
                }
                continue;
            }

            if (c == '"') {
                inQuotedString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripTrailingCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    public record ParsedScript(
            List<AliasDefinition> definitions,
            Map<String, AliasDefinition> aliases,
            List<String> commands,
            List<Diagnostic> diagnostics
    ) {
    }

    public record AliasDefinition(
            String name,
            String expression,
            Map<String, AliasDefinition> visibleAliases,
            int lineIndex
    ) {
    }

    public record Diagnostic(int lineIndex, String message) {
    }

    private record Token(String text, int start, int end) {
    }
}
