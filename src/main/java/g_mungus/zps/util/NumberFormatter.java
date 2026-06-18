package g_mungus.zps.util;

public final class NumberFormatter {
    private NumberFormatter() {
    }

    public static String formatInt(int n) {
        if (n > 1_000_000) {
            return Math.round((double) n / 100_000d) / 10d + "M";
        }
        if (n > 10_000) {
            return Math.round((double) n / 100d) / 10d + "K";
        }
        return n + "";
    }
}
