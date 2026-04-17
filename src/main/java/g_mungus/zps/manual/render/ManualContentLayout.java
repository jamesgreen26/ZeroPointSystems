package g_mungus.zps.manual.render;

import g_mungus.zps.manual.markdown.ManualDocument;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ManualContentLayout {
    public static final float LINE_SPACING_MULTIPLIER = 1.15F;
    private static final int PARAGRAPH_SPACING = 8;
    private static final int LIST_ITEM_SPACING = 3;
    private static final int CODE_PADDING = 6;
    private static final int TABLE_PADDING = 4;
    private static final int TABLE_GAP = 2;
    private static final int CHART_BAR_HEIGHT = 12;
    private static final int TEXT_BASE = 0x4C99C9;
    private static final int TEXT_HIGHLIGHT = 0x79F1A3;
    private static final int TEXT_MUTED = 0xA6B8C5;

    private final List<Entry> entries;
    private final int totalHeight;

    private ManualContentLayout(final List<Entry> entries, final int totalHeight) {
        this.entries = entries;
        this.totalHeight = totalHeight;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public int totalHeight() {
        return this.totalHeight;
    }

    public static ManualContentLayout create(final Minecraft minecraft, final Font font, final ManualDocument document, final int width) {
        final List<Entry> entries = new ArrayList<>();
        int y = 0;

        for (ManualDocument.ManualBlock block : document.blocks()) {
            if (block instanceof ManualDocument.HeadingBlock heading) {
                final MutableComponent text = buildInlineComponent(heading.inlines());
                final Style style = switch (heading.level()) {
                    case 1 -> Style.EMPTY.withBold(true).withColor(TEXT_HIGHLIGHT);
                    case 2 -> Style.EMPTY.withBold(true).withColor(TEXT_MUTED);
                    default -> Style.EMPTY.withBold(true).withColor(TEXT_BASE);
                };
                final int scale = switch (heading.level()) {
                    case 1 -> 2;
                    case 2 -> 1;
                    default -> 1;
                };
                final List<FormattedCharSequence> lines = font.split(text.withStyle(style), Math.max(1, width / scale));
                entries.add(new TextEntry(y, lines, 0, scale, false));
                y += scaledLineStep(font, scale) * lines.size() + PARAGRAPH_SPACING + Math.max(0, 5 - heading.level());
                continue;
            }
            if (block instanceof ManualDocument.ParagraphBlock paragraph) {
                final List<FormattedCharSequence> lines = font.split(buildInlineComponent(paragraph.inlines()), width);
                entries.add(new TextEntry(y, lines, 0, 1, false));
                y += lineStep(font) * lines.size() + PARAGRAPH_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.QuoteBlock quote) {
                final List<FormattedCharSequence> lines = font.split(buildInlineComponent(quote.inlines()).withStyle(ChatFormatting.ITALIC), Math.max(1, width - 14));
                entries.add(new TextEntry(y, lines, 12, 1, true));
                y += lineStep(font) * lines.size() + PARAGRAPH_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.ListBlock list) {
                for (ManualDocument.ListItem item : list.items()) {
                    final int indent = item.depth() * 14;
                    final String marker = item.ordered() ? item.markerNumber() + "." : "\u2022";
                    final List<FormattedCharSequence> lines = font.split(buildInlineComponent(item.inlines()), Math.max(1, width - indent - 18));
                    entries.add(new ListEntry(y, lines, indent, marker));
                    y += lineStep(font) * lines.size() + LIST_ITEM_SPACING;
                }
                y += PARAGRAPH_SPACING - LIST_ITEM_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.RuleBlock) {
                entries.add(new RuleEntry(y));
                y += 13;
                continue;
            }
            if (block instanceof ManualDocument.CodeBlock codeBlock) {
                entries.add(new CodeEntry(y, codeBlock.language(), List.copyOf(codeBlock.lines())));
                y += entries.get(entries.size() - 1).height(font) + PARAGRAPH_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.ImageBlock imageBlock) {
                entries.add(createImageEntry(imageBlock, y));
                y += entries.get(entries.size() - 1).height(font) + PARAGRAPH_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.ChartBlock chart) {
                entries.add(new ChartEntry(y, chart));
                y += entries.get(entries.size() - 1).height(font) + PARAGRAPH_SPACING;
                continue;
            }
            if (block instanceof ManualDocument.TableBlock table) {
                final TableEntry entry = createTableEntry(font, width, y, table);
                entries.add(entry);
                y += entry.height(font) + PARAGRAPH_SPACING;
            }
        }

        return new ManualContentLayout(List.copyOf(entries), Math.max(0, y));
    }

    private static ImageEntry createImageEntry(final ManualDocument.ImageBlock imageBlock, final int y) {
        if (imageBlock.kind() == ManualDocument.ImageKind.ITEM) {
            final String itemId = imageBlock.target().substring("item:".length());
            final Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
            final ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
            return new ImageEntry(y, imageBlock.kind(), imageBlock.target(), Component.literal(imageBlock.altText()), stack, 24, 24);
        }
        return new ImageEntry(y, imageBlock.kind(), imageBlock.target(), Component.literal(imageBlock.altText()), ItemStack.EMPTY, 112, 64);
    }

    private static TableEntry createTableEntry(final Font font, final int width, final int y, final ManualDocument.TableBlock table) {
        final int columns = Math.max(1, Math.max(table.headers().size(), table.rows().stream().mapToInt(row -> row.cells().size()).max().orElse(1)));
        final int totalGap = Math.max(0, columns - 1) * TABLE_GAP;
        final int cellWidth = Math.max(24, (width - totalGap) / columns);
        final List<TableRowLayout> rows = new ArrayList<>();
        int totalHeight = 0;
        rows.add(layoutRow(font, table.headers(), cellWidth, true));
        totalHeight += rows.get(0).height();
        for (ManualDocument.TableRow row : table.rows()) {
            final TableRowLayout rowLayout = layoutRow(font, row.cells(), cellWidth, false);
            rows.add(rowLayout);
            totalHeight += rowLayout.height();
        }
        return new TableEntry(y, rows, List.copyOf(table.alignments()), columns, cellWidth, totalHeight);
    }

    private static TableRowLayout layoutRow(final Font font, final List<ManualDocument.TableCell> cells, final int cellWidth, final boolean header) {
        final List<List<FormattedCharSequence>> renderedCells = new ArrayList<>();
        int rowHeight = 0;
        for (ManualDocument.TableCell cell : cells) {
            final MutableComponent component = buildInlineComponent(cell.inlines());
            final List<FormattedCharSequence> lines = font.split(header ? component.withStyle(ChatFormatting.BOLD) : component, Math.max(1, cellWidth - TABLE_PADDING * 2));
            renderedCells.add(lines);
            rowHeight = Math.max(rowHeight, lines.size() * lineStep(font) + TABLE_PADDING * 2);
        }
        return new TableRowLayout(renderedCells, rowHeight);
    }

    public static int lineStep(final Font font) {
        return Math.max(font.lineHeight, Math.round(font.lineHeight * LINE_SPACING_MULTIPLIER));
    }

    public static int scaledLineStep(final Font font, final int scale) {
        return lineStep(font) * scale;
    }

    private static MutableComponent buildInlineComponent(final List<ManualDocument.ManualInline> inlines) {
        final MutableComponent root = Component.empty();
        for (ManualDocument.ManualInline inline : inlines) {
            if (inline instanceof ManualDocument.TextInline textInline) {
                root.append(Component.literal(textInline.text()));
                continue;
            }
            if (inline instanceof ManualDocument.StyledInline styledInline) {
                Style style = Style.EMPTY;
                if (styledInline.bold()) {
                    style = style.withBold(true);
                }
                if (styledInline.italic()) {
                    style = style.withItalic(true);
                }
                if (styledInline.code()) {
                    style = style.withColor(TEXT_HIGHLIGHT);
                }
                if (styledInline.strikethrough()) {
                    style = style.withStrikethrough(true);
                }
                if (styledInline.linkTarget() != null) {
                    final String target = styledInline.linkTarget();
                    final ClickEvent.Action action = target.endsWith(".md")
                        ? ClickEvent.Action.CHANGE_PAGE
                        : (target.startsWith("http://") || target.startsWith("https://"))
                            ? ClickEvent.Action.OPEN_URL
                            : ClickEvent.Action.COPY_TO_CLIPBOARD;
                    style = style.withColor(TEXT_BASE)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(action, target))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(styledInline.linkTooltip() == null ? target : styledInline.linkTooltip())));
                }
                root.append(Component.literal(styledInline.text()).setStyle(style));
            }
        }
        return root;
    }

    public sealed interface Entry permits TextEntry, ListEntry, RuleEntry, CodeEntry, ImageEntry, ChartEntry, TableEntry {
        int y();

        int height(Font font);
    }

    public record TextEntry(int y, List<FormattedCharSequence> lines, int xOffset, int scale, boolean quoted) implements Entry {
        @Override
        public int height(final Font font) {
            return this.lines.size() * scaledLineStep(font, this.scale);
        }
    }

    public record ListEntry(int y, List<FormattedCharSequence> lines, int indent, String marker) implements Entry {
        @Override
        public int height(final Font font) {
            return this.lines.size() * lineStep(font);
        }
    }

    public record RuleEntry(int y) implements Entry {
        @Override
        public int height(final Font font) {
            return 5;
        }
    }

    public record CodeEntry(int y, String language, List<String> lines) implements Entry {
        @Override
        public int height(final Font font) {
            final int lineCount = this.lines.size() + (this.language.isBlank() ? 0 : 1);
            return Math.max(lineStep(font) + CODE_PADDING * 2, lineCount * lineStep(font) + CODE_PADDING * 2);
        }
    }

    public record ImageEntry(int y, ManualDocument.ImageKind kind, String target, Component label, ItemStack stack, int width, int heightPixels) implements Entry {
        @Override
        public int height(final Font font) {
            return this.heightPixels;
        }
    }

    public record ChartEntry(int y, ManualDocument.ChartBlock chart) implements Entry {
        @Override
        public int height(final Font font) {
            return Math.max(54, this.chart.values().size() * (CHART_BAR_HEIGHT + 6) + 26);
        }

        public double maxValue() {
            return this.chart.values().stream().map(ManualDocument.ChartValue::value).max(Comparator.naturalOrder()).orElse(1.0D);
        }
    }

    public record TableEntry(
        int y,
        List<TableRowLayout> rows,
        List<ManualDocument.TableAlignment> alignments,
        int columns,
        int cellWidth,
        int totalHeight
    ) implements Entry {
        @Override
        public int height(final Font font) {
            return this.totalHeight;
        }
    }

    public record TableRowLayout(List<List<FormattedCharSequence>> cells, int height) {
    }
}
