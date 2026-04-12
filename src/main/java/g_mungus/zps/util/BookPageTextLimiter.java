package g_mungus.zps.util;

public final class BookPageTextLimiter {
    private static final int MAX_TEXT_LENGTH = 1023;
    private static final int TEXT_WIDTH_X256 = 114 * 256;
    private static final int MAX_LINES = 128 / 9;

    private BookPageTextLimiter() {
    }

    public static String truncateToDisplayableLength(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        int end = 0;
        for (int i = 0; i < original.length(); ) {
            int codePoint = original.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (next > MAX_TEXT_LENGTH) {
                break;
            }
            if (countWrappedLines(original, next) > MAX_LINES) {
                break;
            }
            end = next;
            i = next;
        }

        return original.substring(0, end);
    }

    private static int countWrappedLines(String text, int endExclusive) {
        int lineCount = 0;
        int start = 0;

        while (start < endExclusive) {
            lineCount++;
            start = findNextLineStart(text, start, endExclusive);
        }

        return lineCount;
    }

    private static int findNextLineStart(String text, int start, int endExclusive) {
        int widthX256 = 0;
        boolean hadNonZeroWidthChar = false;
        int lastSpace = -1;
        int nextChar = start;

        for (int i = start; i < endExclusive; ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '\n') {
                return i + charCount;
            }

            if (codePoint == ' ') {
                lastSpace = i;
            }

            widthX256 += BookCharacterWidthTable.widthX256(codePoint);
            if (hadNonZeroWidthChar && widthX256 > TEXT_WIDTH_X256) {
                return lastSpace != -1 ? lastSpace + 1 : i;
            }

            if (BookCharacterWidthTable.widthX256(codePoint) != 0) {
                hadNonZeroWidthChar = true;
            }

            nextChar = i + charCount;
            i = nextChar;
        }

        return nextChar;
    }
}
