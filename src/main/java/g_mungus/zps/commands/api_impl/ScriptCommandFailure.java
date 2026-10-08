package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import net.minecraft.nbt.CompoundTag;

/**
 * Why a script command did not run, in words a player can act on, plus where in the command it
 * went wrong. Kept free of exception names and stack traces, so it can be shown on a screen as-is.
 *
 * @param reason     what went wrong, one sentence
 * @param evaluated  no longer written: a fault inside a {@code value_of} is located in the command
 *                   itself. Still read, so a failure saved before that keeps its tooltip.
 * @param faultStart start of the offending span within the player's command, or within
 *                   {@code evaluated} when that is set; {@code -1} when unknown
 * @param faultEnd   end of that span, exclusive
 */
public record ScriptCommandFailure(String reason, String evaluated, int faultStart, int faultEnd) {

    private static final String REASON_TAG = "Reason";
    private static final String EVALUATED_TAG = "Evaluated";
    private static final String START_TAG = "FaultStart";
    private static final String END_TAG = "FaultEnd";

    public static ScriptCommandFailure of(String reason) {
        return new ScriptCommandFailure(reason, "", -1, -1);
    }

    public static ScriptCommandFailure of(ScriptFailure failure) {
        StringRange range = failure.faultRange();
        return range == null
                ? of(failure.reason())
                : new ScriptCommandFailure(failure.reason(), "", range.getStart(), range.getEnd());
    }

    public boolean hasFault() {
        return faultStart >= 0 && faultEnd > faultStart;
    }

    /** Whether the fault range points into the player's own command rather than a nested expression. */
    public boolean faultInCommand() {
        return hasFault() && evaluated.isEmpty();
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString(REASON_TAG, reason);
        tag.putString(EVALUATED_TAG, evaluated);
        tag.putInt(START_TAG, faultStart);
        tag.putInt(END_TAG, faultEnd);
        return tag;
    }

    public static ScriptCommandFailure load(CompoundTag tag) {
        return new ScriptCommandFailure(tag.getString(REASON_TAG), tag.getString(EVALUATED_TAG),
                tag.getInt(START_TAG), tag.getInt(END_TAG));
    }
}
