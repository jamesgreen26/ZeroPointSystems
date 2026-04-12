package g_mungus.zps.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookPageTextLimiterTest {

    @Test
    void truncatesShortButTooWideText() {
        String original = "W".repeat(400);

        String truncated = BookPageTextLimiter.truncateToDisplayableLength(original);

        assertTrue(truncated.length() < original.length());
        assertEquals("W".repeat(266), truncated);
    }

    @Test
    void truncatesAtFourteenthVisibleLine() {
        String original = ("a\n").repeat(20);

        String truncated = BookPageTextLimiter.truncateToDisplayableLength(original);

        assertEquals(("a\n").repeat(14), truncated);
    }
}
