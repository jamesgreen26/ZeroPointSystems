package g_mungus.zps.blockentity.light_pipe;

import org.joml.Vector2ic;
import org.joml.Vector3ic;

public interface LightPipeDataReceiver {

    interface Text extends LightPipeDataReceiver {
        void acceptText(String message);

        int getMaxLength();
    }

    interface Video extends LightPipeDataReceiver {
        void acceptNextFrame(Vector3ic[][] frame);

        Vector2ic getResolution();
    }
}
