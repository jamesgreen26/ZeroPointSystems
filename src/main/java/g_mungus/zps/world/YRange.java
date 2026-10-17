package g_mungus.zps.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** An inclusive Y band, written in data as {@code "min..max"} or a single number; {@code *} leaves an end unbounded. */
public record YRange(int min, int max) {
    public static final Codec<YRange> CODEC = Codec.STRING.comapFlatMap(YRange::parse, YRange::toString);
    /** Every height, written {@code "*"}. */
    public static final YRange ALL = new YRange(Integer.MIN_VALUE, Integer.MAX_VALUE);

    public boolean contains(int y) {
        return y >= min && y <= max;
    }

    private static DataResult<YRange> parse(String text) {
        if (text.trim().equals("*")) return DataResult.success(ALL);
        int separator = text.indexOf("..");
        try {
            int min = parseEnd(separator < 0 ? text : text.substring(0, separator), Integer.MIN_VALUE);
            int max = separator < 0 ? min : parseEnd(text.substring(separator + 2), Integer.MAX_VALUE);
            if (min > max) return DataResult.error(() -> "Y range " + text + " has min above max");
            return DataResult.success(new YRange(min, max));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a Y range (expected \"min..max\"): " + text);
        }
    }

    private static int parseEnd(String end, int unbounded) {
        end = end.trim();
        return end.equals("*") ? unbounded : Integer.parseInt(end);
    }

    private static String formatEnd(int end, int unbounded) {
        return end == unbounded ? "*" : Integer.toString(end);
    }

    @Override
    public String toString() {
        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) return "*";
        if (min == max) return Integer.toString(min);
        return formatEnd(min, Integer.MIN_VALUE) + ".." + formatEnd(max, Integer.MAX_VALUE);
    }

    /** Fails if any two of {@code bands}' ranges overlap. */
    public static <V> DataResult<Map<YRange, V>> checkNoOverlap(Map<YRange, V> bands) {
        List<YRange> ranges = new ArrayList<>(bands.keySet());
        ranges.sort(Comparator.comparingInt(YRange::min));
        for (int i = 1; i < ranges.size(); i++) {
            YRange previous = ranges.get(i - 1);
            YRange current = ranges.get(i);
            if (current.min() <= previous.max()) {
                return DataResult.error(() -> "Overlapping Y ranges " + previous + " and " + current);
            }
        }
        return DataResult.success(bands);
    }
}
