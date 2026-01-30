package g_mungus.zps.blockentity.light_pipe;

import org.joml.Vector2ic;
import org.joml.Vector3ic;

public interface LightPipeDataReceiver {

    interface Text extends LightPipeDataReceiver {
        void acceptText(int channel, String message);

        /// beware, text with length < 512 will be considered safe to write to a book
        default int getMaxLength() {
            return 900; // 30x30 is max resolution for text display
        }
    }

    interface Video extends LightPipeDataReceiver {
        void acceptNextFrame(int channel, Vector3ic[][] frame);

        Vector2ic getResolution();
    }
}
