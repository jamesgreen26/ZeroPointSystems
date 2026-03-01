package g_mungus.zps.client.tts;

import com.sun.speech.freetts.audio.AudioPlayer;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MemoryAudioPlayer implements AudioPlayer {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private AudioFormat format;
    private float volume = 1.0f;

    @Override
    public void setAudioFormat(AudioFormat format) { this.format = format; }

    @Override
    public AudioFormat getAudioFormat() { return format; }

    @Override
    public boolean write(byte[] data, int offset, int size) {
        buffer.write(data, offset, size);
        return true;
    }

    @Override
    public boolean write(byte[] bytes) {
        try {
            buffer.write(bytes);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public byte[] getAudioData() {
        return buffer.toByteArray();
    }

    // No-op implementations
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void reset() {}
    @Override public boolean drain() { return true; }
    @Override public void begin(int size) {}
    @Override public boolean end() { return true; }
    @Override public void cancel() {}
    @Override public void close() {}
    @Override public float getVolume() { return volume; }
    @Override public void setVolume(float v) { volume = v; }
    @Override public long getTime() { return -1L; }
    @Override public void resetTime() {}
    @Override public void startFirstSampleTimer() {}
    @Override public void showMetrics() {}
}