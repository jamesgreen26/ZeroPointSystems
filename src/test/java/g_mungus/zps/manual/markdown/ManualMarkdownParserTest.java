package g_mungus.zps.manual.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ManualMarkdownParserTest {

    @Test
    public void parse_blocks_coversHeadingsParagraphsListsQuotesRulesCodeTablesImagesAndCharts() {
        final String markdown = """
            # Heading

            Paragraph with **bold**, *italic*, ***both***, ~~strike~~, `code`, and [link](examples.md "Examples tab").

            > quoted text

            - bullet
              - nested bullet
            2. ordered

            ---

            | Name | Value |
            | :--- | ---: |
            | One | 1 |

            ![Terminal](item:zps:script_terminal)

            ```java
            int x = 1;
            ```

            ```chart
            title: Sample
            type: line
            alpha: 1
            beta: 2
            ```
            """;

        final ManualDocument document = ManualMarkdownParser.parse(markdown);

        assertEquals(9, document.blocks().size());
        assertInstanceOf(ManualDocument.HeadingBlock.class, document.blocks().get(0));
        assertInstanceOf(ManualDocument.ParagraphBlock.class, document.blocks().get(1));
        assertInstanceOf(ManualDocument.QuoteBlock.class, document.blocks().get(2));
        assertInstanceOf(ManualDocument.ListBlock.class, document.blocks().get(3));
        assertInstanceOf(ManualDocument.RuleBlock.class, document.blocks().get(4));
        assertInstanceOf(ManualDocument.TableBlock.class, document.blocks().get(5));
        assertInstanceOf(ManualDocument.ImageBlock.class, document.blocks().get(6));
        assertInstanceOf(ManualDocument.CodeBlock.class, document.blocks().get(7));
        assertInstanceOf(ManualDocument.ChartBlock.class, document.blocks().get(8));
    }

    @Test
    public void parse_inlines_coversBoldItalicCodeStrikeAndLinks() {
        final List<ManualDocument.ManualInline> inlines = ManualMarkdownParser.parseInlines(
            "plain **bold** *italic* ***both*** ~~gone~~ `code` [docs](https://example.com \"Example\")"
        );

        final ManualDocument.StyledInline bold = findStyled(inlines, "bold");
        assertTrue(bold.bold());
        assertFalse(bold.italic());

        final ManualDocument.StyledInline italic = findStyled(inlines, "italic");
        assertFalse(italic.bold());
        assertTrue(italic.italic());

        final ManualDocument.StyledInline both = findStyled(inlines, "both");
        assertTrue(both.bold());
        assertTrue(both.italic());

        final ManualDocument.StyledInline strike = findStyled(inlines, "gone");
        assertTrue(strike.strikethrough());

        final ManualDocument.StyledInline code = findStyled(inlines, "code");
        assertTrue(code.code());

        final ManualDocument.StyledInline link = findStyled(inlines, "docs");
        assertEquals("https://example.com", link.linkTarget());
        assertEquals("Example", link.linkTooltip());
    }

    @Test
    public void parse_links_preservesNestedStyleInsideLabel() {
        final List<ManualDocument.ManualInline> inlines = ManualMarkdownParser.parseInlines("[**Bold** _Italic_](examples.md)");

        assertEquals(3, inlines.size());
        final ManualDocument.StyledInline first = findStyled(inlines, "Bold");
        final ManualDocument.StyledInline separator = findStyled(inlines, " ");
        final ManualDocument.StyledInline second = findStyled(inlines, "Italic");
        assertTrue(first.bold());
        assertEquals("examples.md", first.linkTarget());
        assertEquals("examples.md", separator.linkTarget());
        assertTrue(second.italic());
        assertEquals("examples.md", second.linkTarget());
    }

    @Test
    public void parse_tables_recognizesAlignmentsAndEscapedPipes() {
        final ManualDocument document = ManualMarkdownParser.parse("""
            | Left | Center | Right |
            | :--- | :----: | ----: |
            | a\\|b | c | d |
            """);

        final ManualDocument.TableBlock table = assertInstanceOf(ManualDocument.TableBlock.class, document.blocks().get(0));
        assertEquals(List.of(
            ManualDocument.TableAlignment.LEFT,
            ManualDocument.TableAlignment.CENTER,
            ManualDocument.TableAlignment.RIGHT
        ), table.alignments());
        assertEquals("a|b", ((ManualDocument.TextInline) table.rows().get(0).cells().get(0).inlines().get(0)).text());
    }

    @Test
    public void parse_images_classifiesItemResourceExternalAndUnknownTargets() {
        final ManualDocument itemDoc = ManualMarkdownParser.parse("![A](item:zps:script_terminal)");
        final ManualDocument.ImageBlock itemImage = assertInstanceOf(ManualDocument.ImageBlock.class, itemDoc.blocks().get(0));
        assertEquals(ManualDocument.ImageKind.ITEM, itemImage.kind());

        final ManualDocument resourceDoc = ManualMarkdownParser.parse("![A](zps:textures/gui/manual.png)");
        final ManualDocument.ImageBlock resourceImage = assertInstanceOf(ManualDocument.ImageBlock.class, resourceDoc.blocks().get(0));
        assertEquals(ManualDocument.ImageKind.RESOURCE, resourceImage.kind());

        final ManualDocument externalDoc = ManualMarkdownParser.parse("![A](https://example.com/manual.png)");
        final ManualDocument.ImageBlock externalImage = assertInstanceOf(ManualDocument.ImageBlock.class, externalDoc.blocks().get(0));
        assertEquals(ManualDocument.ImageKind.EXTERNAL, externalImage.kind());

        final ManualDocument unknownDoc = ManualMarkdownParser.parse("![A](manual.png)");
        final ManualDocument.ImageBlock unknownImage = assertInstanceOf(ManualDocument.ImageBlock.class, unknownDoc.blocks().get(0));
        assertEquals(ManualDocument.ImageKind.UNKNOWN, unknownImage.kind());
    }

    @Test
    public void parse_chart_acceptsBarAndLineTypesAndRejectsMalformedData() {
        final ManualDocument chartDoc = ManualMarkdownParser.parse("""
            ```chart
            title: Metrics
            type: bar
            alpha: 1.5
            beta: 2.5
            ```
            """);

        final ManualDocument.ChartBlock chart = assertInstanceOf(ManualDocument.ChartBlock.class, chartDoc.blocks().get(0));
        assertEquals(ManualDocument.ChartType.BAR, chart.type());
        assertEquals(2, chart.values().size());

        final ManualDocument fallbackDoc = ManualMarkdownParser.parse("""
            ```chart
            type: nonsense
            alpha: nope
            ```
            """);

        final ManualDocument.CodeBlock fallback = assertInstanceOf(ManualDocument.CodeBlock.class, fallbackDoc.blocks().get(0));
        assertEquals("chart", fallback.language());
    }

    @Test
    public void parse_lists_tracksDepthMarkerNumbersAndContinuationLines() {
        final ManualDocument document = ManualMarkdownParser.parse("""
            - top
              continuation
              - nested
            5. ordered
            """);

        final ManualDocument.ListBlock list = assertInstanceOf(ManualDocument.ListBlock.class, document.blocks().get(0));
        assertEquals(3, list.items().size());
        assertEquals(0, list.items().get(0).depth());
        assertEquals("top continuation", extractText(list.items().get(0).inlines()));
        assertEquals(1, list.items().get(1).depth());
        assertFalse(list.items().get(0).ordered());
        assertTrue(list.items().get(2).ordered());
        assertEquals(5, list.items().get(2).markerNumber());
    }

    @Test
    public void parse_malformedInlineSyntaxFallsBackToLiteralText() {
        final List<ManualDocument.ManualInline> inlines = ManualMarkdownParser.parseInlines("**bold *broken [link](x");
        assertEquals("**bold *broken [link](x", extractText(inlines));
    }

    @Test
    public void parse_escapedMarkdownCharactersRemainLiteral() {
        final List<ManualDocument.ManualInline> inlines = ManualMarkdownParser.parseInlines("\\*not italic\\* and \\[not link](x)");
        assertEquals(1, inlines.size());
        assertEquals("*not italic* and [not link](x)", ((ManualDocument.TextInline) inlines.get(0)).text());
    }

    private static String extractText(final List<ManualDocument.ManualInline> inlines) {
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

    private static ManualDocument.StyledInline findStyled(final List<ManualDocument.ManualInline> inlines, final String text) {
        for (ManualDocument.ManualInline inline : inlines) {
            if (inline instanceof ManualDocument.StyledInline styledInline && styledInline.text().equals(text)) {
                return styledInline;
            }
        }
        fail("Missing styled inline: " + text);
        return null;
    }
}
