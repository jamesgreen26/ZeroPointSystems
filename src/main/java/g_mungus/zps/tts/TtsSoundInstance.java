package g_mungus.zps.tts;

import g_mungus.zps.ZPSMod;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.SampledFloat;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class TtsSoundInstance implements SoundInstance {
    private final byte[] audioData;
    private final AudioFormat format;
    private final double x, y, z;

    public static final Sound SOUND = new Sound(
            "zps:tts_sound",
            ConstantFloat.of(1f),
            ConstantFloat.of(1f),
            1,
            Sound.Type.FILE,
            false, false, 64
    );

    public TtsSoundInstance(byte[] audioData, AudioFormat format, double x, double y, double z) {
        this.audioData = audioData;
        this.format = format;
        this.x = x; this.y = y; this.z = z;
    }

    @Override
    public @NotNull ResourceLocation getLocation() { return ZPSMod.resource("tts_sound"); }

    @Override
    public WeighedSoundEvents resolve(@NotNull SoundManager manager) {
        var weightedSound = new WeighedSoundEvents(ZPSMod.resource("tts_sound"), "tts_sound");
        weightedSound.addSound(SOUND);
        return weightedSound;
    }

    @Override
    public @NotNull Sound getSound() { return SOUND; }

    @Override public @NotNull SoundSource getSource() { return SoundSource.MASTER; }
    @Override public boolean isLooping() { return false; }
    @Override public boolean isRelative() { return true; }
    @Override public int getDelay() { return 0; }
    @Override public float getVolume() { return 1.0f; }
    @Override public float getPitch() { return 1.0f; }
    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getZ() { return z; }
    @Override public @NotNull Attenuation getAttenuation() { return Attenuation.LINEAR; }

    public AudioFormat getFormat() { return format; }
    public byte[] getAudioData() { return audioData; }

    @Override
    public @NotNull CompletableFuture<AudioStream> getStream(@NotNull SoundBufferLibrary soundBuffers, @NotNull Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(new AudioStream() {
            private int position = 0;

            @Override
            public @NotNull AudioFormat getFormat() { return format; }

            @Override
            public @NotNull ByteBuffer read(int maxBytes) {
                if (position >= audioData.length) {
                    // return an empty direct buffer instead of null
                    return ByteBuffer.allocateDirect(0);
                }

                int end = Math.min(position + maxBytes, audioData.length);

                // Use a direct buffer for OpenAL
                ByteBuffer buffer = ByteBuffer.allocateDirect(end - position);
                buffer.put(audioData, position, end - position);
                buffer.flip(); // important!
                position = end;

                return buffer;
            }

            @Override
            public void close() {}
        });
    }
}