package g_mungus.zps.manual.markdown;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ManualMarkdownParser {
    private ManualMarkdownParser() {
    }

    public static ManualDocument parse(final String markdown) {
        final List<ManualDocument.ManualBlock> blocks = new ArrayList<>();
        final String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        final String[] lines = normalized.split("\n", -1);

        int index = 0;
        while (index < lines.length) {
            final String line = lines[index];
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                index++;
                continue;
            }

            if (isFenceStart(trimmed)) {
                index = parseFencedBlock(lines, index, blocks);
                continue;
            }
            if (isRule(trimmed)) {
                blocks.add(new ManualDocument.RuleBlock());
                index++;
                continue;
            }
            if (isHeading(trimmed)) {
                blocks.add(parseHeading(trimmed));
                index++;
                continue;
            }
            if (isTableStart(lines, index)) {
                index = parseTable(lines, index, blocks);
                continue;
            }
            if (trimmed.startsWith(">")) {
                index = parseQuote(lines, index, blocks);
                continue;
            }
            if (isListLine(line)) {
                index = parseList(lines, index, blocks);
                continue;
            }
            final ManualDocument.ImageBlock imageBlock = parseStandaloneImage(trimmed);
            if (imageBlock != null) {
                blocks.add(imageBlock);
                index++;
                continue;
            }

            index = parseParagraph(lines, index, blocks);
        }

        return new ManualDocument(List.copyOf(blocks));
    }

    private static int parseFencedBlock(final String[] lines, final int start, final List<ManualDocument.ManualBlock> blocks) {
        final String opening = lines[start].trim();
        final int fenceLength = countFenceLength(opening);
        final String language = opening.substring(fenceLength).trim();
        final String fence = "`".repeat(fenceLength);
        final List<String> codeLines = new ArrayList<>();
        int index = start + 1;
        while (index < lines.length && !lines[index].trim().startsWith(fence)) {
            codeLines.add(lines[index]);
            index++;
        }

        if ("chart".equalsIgnoreCase(language) || "zps-chart".equalsIgnoreCase(language)) {
            final ManualDocument.ChartBlock chart = parseChart(codeLines);
            if (chart != null) {
                blocks.add(chart);
            } else {
                blocks.add(new ManualDocument.CodeBlock(language, List.copyOf(codeLines)));
            }
        } else {
            blocks.add(new ManualDocument.CodeBlock(language, List.copyOf(codeLines)));
        }

        return Math.min(index + 1, lines.length);
    }

    private static ManualDocument.ChartBlock parseChart(final List<String> codeLines) {
        final List<ManualDocument.ChartValue> values = new ArrayList<>();
        String title = "Chart";
        ManualDocument.ChartType type = ManualDocument.ChartType.BAR;
        for (String line : codeLines) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int separatorIndex = trimmed.indexOf(':');
            if (separatorIndex < 0) {
                return null;
            }
            final String key = trimmed.substring(0, separatorIndex).trim();
            final String valueText = trimmed.substring(separatorIndex + 1).trim();
            if ("title".equalsIgnoreCase(key)) {
                title = valueText;
                continue;
            }
            if ("type".equalsIgnoreCase(key)) {
                if ("line".equalsIgnoreCase(valueText)) {
                    type = ManualDocument.ChartType.LINE;
                } else if ("bar".equalsIgnoreCase(valueText)) {
                    type = ManualDocument.ChartType.BAR;
                } else {
                    return null;
                }
                continue;
            }
            try {
                values.add(new ManualDocument.ChartValue(key, Double.parseDouble(valueText)));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return values.isEmpty() ? null : new ManualDocument.ChartBlock(Component.literal(title), type, List.copyOf(values));
    }

    private static int parseQuote(final String[] lines, final int start, final List<ManualDocument.ManualBlock> blocks) {
        final StringBuilder builder = new StringBuilder();
        int index = start;
        while (index < lines.length) {
            final String trimmed = lines[index].trim();
            if (!trimmed.startsWith(">")) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(trimmed.substring(1).trim());
            index++;
        }
        blocks.add(new ManualDocument.QuoteBlock(parseInlines(builder.toString().replace('\n', ' '))));
        return index;
    }

    private static int parseList(final String[] lines, final int start, final List<ManualDocument.ManualBlock> blocks) {
        final List<ManualDocument.ListItem> items = new ArrayList<>();
        int index = start;
        while (index < lines.length) {
            if (lines[index].trim().isEmpty()) {
                break;
            }
            if (!isListLine(lines[index])) {
                if (!items.isEmpty() && countIndent(lines[index]) > countIndent(lines[index - 1])) {
                    final ManualDocument.ListItem previous = items.remove(items.size() - 1);
                    final String merged = inlineText(previous.inlines()) + " " + lines[index].trim();
                    items.add(new ManualDocument.ListItem(previous.depth(), previous.ordered(), previous.markerNumber(), parseInlines(merged)));
                    index++;
                    continue;
                }
                break;
            }
            final String trimmed = lines[index].trim();
            final boolean ordered = isOrderedListLine(lines[index]);
            final int indent = countIndent(lines[index]) / 2;
            final int markerNumber = ordered ? parseMarkerNumber(trimmed) : -1;
            final int contentStart = ordered ? trimmed.indexOf('.') + 1 : 1;
            final String content = trimmed.substring(contentStart).trim();
            items.add(new ManualDocument.ListItem(indent, ordered, markerNumber, parseInlines(content)));
            index++;
        }
        blocks.add(new ManualDocument.ListBlock(List.copyOf(items)));
        return index;
    }

    private static int parseTable(final String[] lines, final int start, final List<ManualDocument.ManualBlock> blocks) {
        final List<ManualDocument.TableCell> headers = parseTableCells(lines[start]);
        final List<ManualDocument.TableAlignment> alignments = parseTableAlignments(lines[start + 1]);
        final List<ManualDocument.TableRow> rows = new ArrayList<>();
        int index = start + 2;
        while (index < lines.length && isTableRow(lines[index])) {
            final List<ManualDocument.TableCell> cells = parseTableCells(lines[index]);
            rows.add(new ManualDocument.TableRow(cells));
            index++;
        }
        blocks.add(new ManualDocument.TableBlock(headers, alignments, List.copyOf(rows)));
        return index;
    }

    private static int parseParagraph(final String[] lines, final int start, final List<ManualDocument.ManualBlock> blocks) {
        final StringBuilder builder = new StringBuilder();
        int index = start;
        while (index < lines.length) {
            final String trimmed = lines[index].trim();
            if (trimmed.isEmpty()
                || isHeading(trimmed)
                || trimmed.startsWith(">")
                || isFenceStart(trimmed)
                || isRule(trimmed)
                || isListLine(lines[index])
                || parseStandaloneImage(trimmed) != null
                || isTableStart(lines, index)) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(trimmed);
            index++;
        }
        blocks.add(new ManualDocument.ParagraphBlock(parseInlines(builder.toString())));
        return index;
    }

    private static ManualDocument.ImageBlock parseStandaloneImage(final String line) {
        if (!line.startsWith("![") || !line.endsWith(")")) {
            return null;
        }
        final int altEnd = findMatchingBracket(line, 1, '[', ']');
        if (altEnd < 0 || altEnd + 1 >= line.length() || line.charAt(altEnd + 1) != '(') {
            return null;
        }
        final int targetEnd = line.lastIndexOf(')');
        if (targetEnd <= altEnd + 2) {
            return null;
        }
        final String alt = line.substring(2, altEnd);
        final LinkTarget target = parseLinkTarget(line.substring(altEnd + 2, targetEnd));
        return new ManualDocument.ImageBlock(alt, target.target(), classifyImageTarget(target.target()));
    }

    private static ManualDocument.HeadingBlock parseHeading(final String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return new ManualDocument.HeadingBlock(Math.min(level, 6), parseInlines(line.substring(level).trim()));
    }

    public static List<ManualDocument.ManualInline> parseInlines(final String text) {
        return List.copyOf(parseInlineSegment(text, new InlineStyle()).inlines());
    }

    private static ParseResult parseInlineSegment(final String text, final InlineStyle style) {
        final List<ManualDocument.ManualInline> inlines = new ArrayList<>();
        final StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            final char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length()) {
                plain.append(text.charAt(index + 1));
                index += 2;
                continue;
            }
            final CodeSpan codeSpan = tryReadCodeSpan(text, index);
            if (codeSpan != null) {
                flushPlain(plain, inlines, style);
                inlines.add(style.withCode(true).toInline(codeSpan.content()));
                index = codeSpan.nextIndex();
                continue;
            }
            final ParseAdvance imageAdvance = tryReadInlineImage(text, index);
            if (imageAdvance != null) {
                plain.append(imageAdvance.literalText());
                index = imageAdvance.nextIndex();
                continue;
            }
            final ParseAdvance linkAdvance = tryReadLink(text, index, style, inlines);
            if (linkAdvance != null) {
                flushPlain(plain, inlines, style);
                inlines.addAll(linkAdvance.produced());
                index = linkAdvance.nextIndex();
                continue;
            }
            final String marker = findInlineMarker(text, index);
            if (marker != null) {
                final int end = text.indexOf(marker, index + marker.length());
                if (end > index) {
                    flushPlain(plain, inlines, style);
                    final String inner = text.substring(index + marker.length(), end);
                    final InlineStyle nextStyle = style.apply(marker);
                    inlines.addAll(parseInlineSegment(inner, nextStyle).inlines());
                    index = end + marker.length();
                    continue;
                }
                plain.append(marker);
                index += marker.length();
                continue;
            }
            plain.append(current);
            index++;
        }
        flushPlain(plain, inlines, style);
        return new ParseResult(inlines);
    }

    private static CodeSpan tryReadCodeSpan(final String text, final int index) {
        if (text.charAt(index) != '`') {
            return null;
        }
        int ticks = 1;
        while (index + ticks < text.length() && text.charAt(index + ticks) == '`') {
            ticks++;
        }
        final String marker = "`".repeat(ticks);
        final int end = text.indexOf(marker, index + ticks);
        if (end < 0) {
            return null;
        }
        return new CodeSpan(text.substring(index + ticks, end), end + ticks);
    }

    private static ParseAdvance tryReadInlineImage(final String text, final int index) {
        if (!text.startsWith("![", index)) {
            return null;
        }
        final int altEnd = findMatchingBracket(text, index + 1, '[', ']');
        if (altEnd < 0 || altEnd + 1 >= text.length() || text.charAt(altEnd + 1) != '(') {
            return null;
        }
        final int targetEnd = findMatchingParen(text, altEnd + 1);
        if (targetEnd < 0) {
            return null;
        }
        final String alt = text.substring(index + 2, altEnd);
        return new ParseAdvance(targetEnd + 1, List.of(), "[" + alt + "]");
    }

    private static ParseAdvance tryReadLink(final String text, final int index, final InlineStyle currentStyle, final List<ManualDocument.ManualInline> destination) {
        if (text.charAt(index) != '[') {
            return null;
        }
        final int labelEnd = findMatchingBracket(text, index, '[', ']');
        if (labelEnd < 0 || labelEnd + 1 >= text.length() || text.charAt(labelEnd + 1) != '(') {
            return null;
        }
        final int targetEnd = findMatchingParen(text, labelEnd + 1);
        if (targetEnd < 0) {
            return null;
        }
        final String label = text.substring(index + 1, labelEnd);
        final LinkTarget linkTarget = parseLinkTarget(text.substring(labelEnd + 2, targetEnd));
        final List<ManualDocument.ManualInline> linkedInlines = applyLink(parseInlineSegment(label, currentStyle).inlines(), linkTarget);
        return new ParseAdvance(targetEnd + 1, linkedInlines, null);
    }

    private static List<ManualDocument.ManualInline> applyLink(final List<ManualDocument.ManualInline> parsed, final LinkTarget linkTarget) {
        final List<ManualDocument.ManualInline> linked = new ArrayList<>();
        for (ManualDocument.ManualInline inline : parsed) {
            if (inline instanceof ManualDocument.TextInline textInline) {
                linked.add(new ManualDocument.StyledInline(textInline.text(), false, false, false, false, linkTarget.target(), linkTarget.tooltip()));
            } else if (inline instanceof ManualDocument.StyledInline styledInline) {
                linked.add(new ManualDocument.StyledInline(
                    styledInline.text(),
                    styledInline.bold(),
                    styledInline.italic(),
                    styledInline.code(),
                    styledInline.strikethrough(),
                    linkTarget.target(),
                    linkTarget.tooltip()
                ));
            }
        }
        return linked;
    }

    private static LinkTarget parseLinkTarget(final String rawTarget) {
        final String trimmed = rawTarget.trim();
        if (trimmed.isEmpty()) {
            return new LinkTarget("", "");
        }
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            final String target = trimmed.substring(1, trimmed.length() - 1);
            return new LinkTarget(target, target);
        }
        final int titleStart = findTitleStart(trimmed);
        if (titleStart > 0) {
            final String target = trimmed.substring(0, titleStart).trim();
            final String title = trimmed.substring(titleStart).trim();
            return new LinkTarget(target, stripTitleQuotes(title));
        }
        return new LinkTarget(trimmed, trimmed);
    }

    private static int findTitleStart(final String text) {
        boolean seenSpace = false;
        for (int index = 0; index < text.length(); index++) {
            final char current = text.charAt(index);
            if (current == ' ') {
                seenSpace = true;
                continue;
            }
            if (seenSpace && (current == '"' || current == '\'')) {
                return index;
            }
        }
        return -1;
    }

    private static String stripTitleQuotes(final String title) {
        if (title.length() >= 2) {
            final char first = title.charAt(0);
            final char last = title.charAt(title.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return title.substring(1, title.length() - 1);
            }
        }
        return title;
    }

    private static List<ManualDocument.TableCell> parseTableCells(final String line) {
        final List<String> rawCells = splitTableLine(line);
        final List<ManualDocument.TableCell> cells = new ArrayList<>();
        for (String rawCell : rawCells) {
            cells.add(new ManualDocument.TableCell(parseInlines(rawCell.trim())));
        }
        return List.copyOf(cells);
    }

    private static List<ManualDocument.TableAlignment> parseTableAlignments(final String line) {
        final List<String> rawCells = splitTableLine(line);
        final List<ManualDocument.TableAlignment> alignments = new ArrayList<>();
        for (String rawCell : rawCells) {
            final String trimmed = rawCell.trim();
            final boolean left = trimmed.startsWith(":");
            final boolean right = trimmed.endsWith(":");
            if (left && right) {
                alignments.add(ManualDocument.TableAlignment.CENTER);
            } else if (right) {
                alignments.add(ManualDocument.TableAlignment.RIGHT);
            } else {
                alignments.add(ManualDocument.TableAlignment.LEFT);
            }
        }
        return List.copyOf(alignments);
    }

    private static List<String> splitTableLine(final String line) {
        final String trimmed = stripTableEdgePipes(line.trim());
        final List<String> cells = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < trimmed.length(); index++) {
            final char character = trimmed.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '|') {
                cells.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        cells.add(current.toString());
        return cells;
    }

    private static String stripTableEdgePipes(final String line) {
        int start = 0;
        int end = line.length();
        if (start < end && line.charAt(start) == '|') {
            start++;
        }
        if (end > start && line.charAt(end - 1) == '|') {
            end--;
        }
        return line.substring(start, end);
    }

    private static void flushPlain(final StringBuilder plain, final List<ManualDocument.ManualInline> inlines, final InlineStyle style) {
        if (!plain.isEmpty()) {
            inlines.add(style.toInline(plain.toString()));
            plain.setLength(0);
        }
    }

    private static String findInlineMarker(final String text, final int index) {
        final String[] markers = {"***", "___", "**", "__", "~~", "*", "_"};
        for (String marker : markers) {
            if (text.startsWith(marker, index)) {
                return marker;
            }
        }
        return null;
    }

    private static boolean isFenceStart(final String trimmed) {
        return trimmed.startsWith("```");
    }

    private static int countFenceLength(final String trimmed) {
        int count = 0;
        while (count < trimmed.length() && trimmed.charAt(count) == '`') {
            count++;
        }
        return Math.max(3, count);
    }

    private static boolean isHeading(final String trimmed) {
        int count = 0;
        while (count < trimmed.length() && trimmed.charAt(count) == '#') {
            count++;
        }
        return count > 0 && count <= 6 && (count == trimmed.length() || Character.isWhitespace(trimmed.charAt(count)));
    }

    private static boolean isRule(final String trimmed) {
        final String compact = trimmed.replace(" ", "");
        return compact.length() >= 3 && (compact.chars().allMatch(character -> character == '-')
            || compact.chars().allMatch(character -> character == '*')
            || compact.chars().allMatch(character -> character == '_'));
    }

    private static boolean isTableStart(final String[] lines, final int index) {
        return index + 1 < lines.length && isTableRow(lines[index]) && isTableSeparator(lines[index + 1]);
    }

    private static boolean isTableRow(final String line) {
        return line.contains("|");
    }

    private static boolean isTableSeparator(final String line) {
        final List<String> cells = splitTableLine(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            final String trimmed = cell.trim();
            if (trimmed.length() < 3) {
                return false;
            }
            final String compact = trimmed.replace(":", "");
            if (!compact.chars().allMatch(character -> character == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isListLine(final String line) {
        final String trimmed = line.trim();
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") || isOrderedListLine(line);
    }

    private static boolean isOrderedListLine(final String line) {
        final String trimmed = line.trim();
        int digits = 0;
        while (digits < trimmed.length() && Character.isDigit(trimmed.charAt(digits))) {
            digits++;
        }
        return digits > 0 && digits + 1 < trimmed.length() && trimmed.charAt(digits) == '.' && trimmed.charAt(digits + 1) == ' ';
    }

    private static int parseMarkerNumber(final String trimmed) {
        int digits = 0;
        while (digits < trimmed.length() && Character.isDigit(trimmed.charAt(digits))) {
            digits++;
        }
        return Integer.parseInt(trimmed.substring(0, digits));
    }

    private static int countIndent(final String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static int findMatchingBracket(final String text, final int start, final char open, final char close) {
        int depth = 0;
        for (int index = start; index < text.length(); index++) {
            final char current = text.charAt(index);
            if (current == '\\') {
                index++;
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int findMatchingParen(final String text, final int startParenIndex) {
        int depth = 0;
        for (int index = startParenIndex; index < text.length(); index++) {
            final char current = text.charAt(index);
            if (current == '\\') {
                index++;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static ManualDocument.ImageKind classifyImageTarget(final String target) {
        if (target.startsWith("item:")) {
            return ManualDocument.ImageKind.ITEM;
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return ManualDocument.ImageKind.EXTERNAL;
        }
        if (target.contains(":")) {
            return ManualDocument.ImageKind.RESOURCE;
        }
        return ManualDocument.ImageKind.UNKNOWN;
    }

    private static String inlineText(final List<ManualDocument.ManualInline> inlines) {
        final StringBuilder builder = new StringBuilder();
        for (ManualDocument.ManualInline inline : inlines) {
            if (inline instanceof ManualDocument.TextInline textInline) {
                builder.append(textInline.text());
            } else if (inline instanceof ManualDocument.StyledInline styledInline) {
                builder.append(styledInline.text());
            }
        }
        return builder.toString();
    }

    private record ParseResult(List<ManualDocument.ManualInline> inlines) {
    }

    private record ParseAdvance(int nextIndex, List<ManualDocument.ManualInline> produced, String literalText) {
    }

    private record CodeSpan(String content, int nextIndex) {
    }

    private record LinkTarget(String target, String tooltip) {
    }

    private record InlineStyle(boolean bold, boolean italic, boolean code, boolean strikethrough) {
        private InlineStyle() {
            this(false, false, false, false);
        }

        private InlineStyle apply(final String marker) {
            return switch (marker) {
                case "***", "___" -> new InlineStyle(true, true, this.code, this.strikethrough);
                case "**", "__" -> new InlineStyle(true, this.italic, this.code, this.strikethrough);
                case "*", "_" -> new InlineStyle(this.bold, true, this.code, this.strikethrough);
                case "~~" -> new InlineStyle(this.bold, this.italic, this.code, true);
                default -> this;
            };
        }

        private InlineStyle withCode(final boolean codeValue) {
            return new InlineStyle(this.bold, this.italic, codeValue, this.strikethrough);
        }

        private ManualDocument.ManualInline toInline(final String text) {
            if (!this.bold && !this.italic && !this.code && !this.strikethrough) {
                return new ManualDocument.TextInline(text);
            }
            return new ManualDocument.StyledInline(text, this.bold, this.italic, this.code, this.strikethrough, null, null);
        }
    }
}
