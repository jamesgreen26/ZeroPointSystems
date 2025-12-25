package g_mungus.zps.blockentity.light_pipe;

import org.joml.Vector2ic;
import org.joml.Vector3ic;

public interface LightPipeDataReceiver {

    interface Text extends LightPipeDataReceiver {
        void provideText(String message);

        int getMaxLength();
    }

    interface Video extends LightPipeDataReceiver {
        void provideNextFrame(Vector3ic[][] frame);

        Vector2ic getResolution();
    }
}
