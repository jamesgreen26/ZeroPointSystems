package g_mungus.zps.manual.markdown;

import net.minecraft.network.chat.Component;

import java.util.List;

public record ManualDocument(List<ManualBlock> blocks) {
    public interface ManualBlock {
    }

    public record HeadingBlock(int level, List<ManualInline> inlines) implements ManualBlock {
    }

    public record ParagraphBlock(List<ManualInline> inlines) implements ManualBlock {
    }

    public record ListBlock(List<ListItem> items) implements ManualBlock {
    }

    public record ListItem(int depth, boolean ordered, int markerNumber, List<ManualInline> inlines) {
    }

    public record QuoteBlock(List<ManualInline> inlines) implements ManualBlock {
    }

    public record RuleBlock() implements ManualBlock {
    }

    public record CodeBlock(String language, List<String> lines) implements ManualBlock {
    }

    public record ImageBlock(String altText, String target, ImageKind kind) implements ManualBlock {
    }

    public enum ImageKind {
        ITEM,
        RESOURCE,
        EXTERNAL,
        UNKNOWN
    }

    public record ChartBlock(Component title, ChartType type, List<ChartValue> values) implements ManualBlock {
    }

    public enum ChartType {
        BAR,
        LINE
    }

    public record ChartValue(String label, double value) {
    }

    public record TableBlock(List<TableCell> headers, List<TableAlignment> alignments, List<TableRow> rows) implements ManualBlock {
    }

    public record TableRow(List<TableCell> cells) {
    }

    public record TableCell(List<ManualInline> inlines) {
    }

    public enum TableAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public interface ManualInline {
    }

    public record TextInline(String text) implements ManualInline {
    }

    public record StyledInline(
        String text,
        boolean bold,
        boolean italic,
        boolean code,
        boolean strikethrough,
        String linkTarget,
        String linkTooltip
    ) implements ManualInline {
    }
}
