package g_mungus.zps.tts;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.sound.sampled.AudioFormat;

public class TtsSoundsManager {

    private static final long SOUND_TIMEOUT_MS = 60_000; // 1 minute
    private static final AudioFormat DEFAULT_FORMAT = new AudioFormat(8000.0F, 16, 1, true, false);

    /** Maps block positions to active sounds and their creation time */
    private static final Long2ObjectMap<TrackedSound> activeSoundsByPos = new Long2ObjectArrayMap<>();

    public static void speakTextAt(BlockPos pos, String text) {
        long key = pos.asLong();
        Vec3 center = pos.getCenter();

        // Clean up old sounds
        cleanupOldSounds();

        // Generate TTS
        MemoryAudioPlayer player = new MemoryAudioPlayer();
        player.setAudioFormat(DEFAULT_FORMAT);
        Voice voice = new KevinVoiceDirectory().getVoices()[1]; // directly access voice because voice manager is annoying
        voice.allocate();
        voice.setAudioPlayer(player);
        voice.speak(text);

        TtsSoundInstance newSoundInstance = new TtsSoundInstance(player.getAudioData(), DEFAULT_FORMAT, center.x, center.y, center.z);
        TrackedSound trackedSound = new TrackedSound(newSoundInstance, System.currentTimeMillis());

        // Replace old sound if any
        TrackedSound oldTracked = activeSoundsByPos.put(key, trackedSound);

        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (oldTracked != null) {
            soundManager.stop(oldTracked.soundInstance);
        }

        // Play new sound
        soundManager.play(newSoundInstance);
    }

    private static void cleanupOldSounds() {
        long now = System.currentTimeMillis();
        activeSoundsByPos.values().removeIf(tracked -> {
            boolean expired = now - tracked.timestamp > SOUND_TIMEOUT_MS;
            if (expired) {
                Minecraft.getInstance().getSoundManager().stop(tracked.soundInstance);
            }
            return expired;
        });
    }

    private record TrackedSound(TtsSoundInstance soundInstance, long timestamp) { }
}