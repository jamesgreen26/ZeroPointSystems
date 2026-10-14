package g_mungus.zps.blockentity.light_pipe;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * What a Data Cable network carries: nothing, one sender's text, or garble when more than one
 * sender puts text on it at once. Every receiver on the network gets the same signal.
 *
 * @param text the one sender's text, or null when the cable is blank or garbled
 */
public record CableSignal(@Nullable String text, boolean garbled) {

    public static final CableSignal BLANK = new CableSignal(null, false);

    /** The signal {@code texts} make together; blank ones put nothing on the cable. */
    public static CableSignal of(List<String> texts) {
        List<String> sent = texts.stream().filter(text -> !text.isBlank()).toList();
        if (sent.isEmpty()) {
            return BLANK;
        }
        return sent.size() == 1 ? new CableSignal(sent.get(0), false) : new CableSignal(null, true);
    }

    public boolean isBlank() {
        return text == null && !garbled;
    }

    /** The signal as a receiver taking at most {@code length} characters reads it. */
    public String readAs(int length) {
        if (garbled) {
            StringBuilder out = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < length; i++) {
                out.append(Character.toChars(random.nextInt(33, 127)));
            }
            return out.toString();
        }
        return text == null ? "" : text.substring(0, Math.min(text.length(), length));
    }
}
