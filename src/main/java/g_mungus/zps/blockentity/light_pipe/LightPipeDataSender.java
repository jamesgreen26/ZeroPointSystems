package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.NetworkTerminal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector2ic;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.Random;

public interface LightPipeDataSender extends NetworkTerminal {

    default void updateSignal(Level level) {
        boolean clear = isClearSignal(level);

        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            int channel = terminal.channel();
            if (be instanceof LightPipeDataReceiver.Text receiver) {
                if (clear) {
                    receiver.acceptText(channel, provideNextDisplayText(receiver.getMaxLength()));
                } else {
                    receiver.acceptText(channel, generateGarbledString(receiver.getMaxLength()));
                }
            } else if (be instanceof LightPipeDataReceiver.Video receiver) {
                if (clear) {
                    receiver.acceptNextFrame(channel, provideNextVideoFrame(receiver.getResolution()));
                } else {
                    receiver.acceptNextFrame(channel, generateGarbledVideo(receiver.getResolution()));
                }
            }
        }
    }

    Vector3ic[][] provideNextVideoFrame(Vector2ic resolution);
    String provideNextDisplayText(int length);

    private boolean isClearSignal(Level level) {
        int senders = 0;
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender) {
                senders++;
                if (senders > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    ///  call this from your block after the network is updated
    default void onRemove(Level level) {
        LightPipeDataSender sender = null;
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender it) {
                if (sender == null) {
                    sender = it;
                } else {
                    return;
                }
            }
        }
        if (sender != null) sender.updateSignal(level);
    }

    private String generateGarbledString(int length) {
        StringBuilder out = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            out.append(Character.toChars(random.nextInt(32, 128)));
        }
        return out.toString();
    }

    private Vector3ic[][] generateGarbledVideo(Vector2ic resolution) {
        Vector3ic[][] frame = new Vector3ic[resolution.y()][resolution.x()];
        Random random = new Random();
        for (int y = 0; y < resolution.y(); y++) {
            for (int x = 0; x < resolution.x(); x++) {
                int r = random.nextInt(256);
                int g = random.nextInt(256);
                int b = random.nextInt(256);
                frame[y][x] = new Vector3i(r, g, b);
            }
        }
        return frame;
    }
}
