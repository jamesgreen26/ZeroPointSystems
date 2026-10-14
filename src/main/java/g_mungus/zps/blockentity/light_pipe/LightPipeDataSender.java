package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.NetworkTerminal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * A block that puts text on a Data Cable network. The cable is a broadcast: every receiver on the
 * network gets the same {@link CableSignal}, wherever it came from.
 *
 * <p>A sender that changes what it sends calls {@link #updateSignal}, which settles the cable in one
 * go. The plain senders say what they send; the signal they would make is offered to the reactive
 * senders, which decide what to send in response; and the signal everything makes together is
 * delivered once to every receiver. A reactive sender sees only the plain senders, never another
 * reactive one, so one pass always settles it.
 */
public interface LightPipeDataSender extends NetworkTerminal {

    /** What this sender puts on the cable, at most {@code length} characters; blank for nothing. */
    String provideNextDisplayText(int length);

    /** Whether this sender decides what to send from what the rest of the cable carries. */
    default boolean isReactive() {
        return false;
    }

    /**
     * For a reactive sender: the cable would carry {@code prospective} from the plain senders alone,
     * so decide what {@link #provideNextDisplayText} gives for this update.
     */
    default void respondTo(CableSignal prospective) {
    }

    /** Settles the cable after this sender changed what it sends, and delivers the result. */
    default void updateSignal(Level level) {
        List<NetworkNode> nodes = getTerminals(Channels.MAIN);
        Set<LightPipeDataSender> senders = Collections.newSetFromMap(new IdentityHashMap<>());
        senders.add(this);
        for (NetworkNode node : nodes) {
            if (level.getBlockEntity(node.pos()) instanceof LightPipeDataSender sender) {
                senders.add(sender);
            }
        }

        List<String> texts = new ArrayList<>();
        List<LightPipeDataSender> reactive = new ArrayList<>();
        for (LightPipeDataSender sender : senders) {
            if (sender.isReactive()) {
                reactive.add(sender);
            } else {
                texts.add(sender.provideNextDisplayText(LightPipeDataReceiver.Text.MAX_TEXT_LENGTH));
            }
        }
        CableSignal prospective = CableSignal.of(texts);
        for (LightPipeDataSender sender : reactive) {
            sender.respondTo(prospective);
            texts.add(sender.provideNextDisplayText(LightPipeDataReceiver.Text.MAX_TEXT_LENGTH));
        }
        CableSignal signal = CableSignal.of(texts);

        for (NetworkNode node : nodes) {
            BlockEntity be = level.getBlockEntity(node.pos());
            if (be instanceof LightPipeDataReceiver.Text receiver) {
                receiver.acceptText(node.channel(), signal.readAs(receiver.getMaxLength()));
            }
        }
    }

    /** Call this from your block once the network is updated: another sender left on it settles the cable. */
    default void onRemove(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            if (terminal.pos().equals(getWorldPos())) continue;
            if (level.getBlockEntity(terminal.pos()) instanceof LightPipeDataSender sender) {
                sender.updateSignal(level);
                return;
            }
        }
    }
}
