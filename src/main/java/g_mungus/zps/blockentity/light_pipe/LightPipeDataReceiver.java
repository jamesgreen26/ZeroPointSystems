package g_mungus.zps.blockentity.light_pipe;

import org.joml.Vector2ic;
import org.joml.Vector3ic;

public interface LightPipeDataReceiver {

    interface Text extends LightPipeDataReceiver {
        /// The largest text display (2x2 blocks) is a 30x30 grid of 8px character
        /// cells, so a monospace screen fills at 30*30 = 900 characters. Proportional
        /// rendering packs more in, because the narrowest glyphs advance ~2px rather
        /// than a full 8px cell; the worst case is
        /// (30 columns * 8px / 2px) * 30 rows = 3600 characters. Capping here keeps
        /// text from being cut off before the screen is physically full.
        int MAX_TEXT_LENGTH = 3600;

        void acceptText(int channel, String message);

        /// beware, text with length < 512 will be considered safe to write to a book
        default int getMaxLength() {
            return MAX_TEXT_LENGTH;
        }
    }

    interface Video extends LightPipeDataReceiver {
        void acceptNextFrame(int channel, Vector3ic[][] frame);

        Vector2ic getResolution();
    }
}
