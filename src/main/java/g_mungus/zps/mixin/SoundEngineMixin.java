package g_mungus.zps.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import g_mungus.zps.client.tts.TtsSoundInstance;
import g_mungus.zps.compat.ClientCompat;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * Intercept the part where SoundEngine attaches a static buffer for non-streaming sounds
     * and supply our TtsSoundInstance audio data.
     */
    @Redirect(
        method = "play",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getCompleteBuffer(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<SoundBuffer> redirectGetCompleteBuffer(SoundBufferLibrary library, ResourceLocation path, SoundInstance instance) {
        TtsSoundInstance tts = zps$findTtsSoundInstance(instance);
        if (tts != null) {
            // Convert the TTS byte array into a direct SoundBuffer (required by OpenAL)
            byte[] audioData = tts.getAudioData();
            ByteBuffer buffer = ByteBuffer.allocateDirect(audioData.length).put(audioData).flip();
            SoundBuffer soundBuffer = new SoundBuffer(buffer, tts.getFormat());
            return CompletableFuture.completedFuture(soundBuffer);
        }

        // fallback to normal behavior for other sounds
        return library.getCompleteBuffer(path);
    }

    @Unique
    private static TtsSoundInstance zps$findTtsSoundInstance(SoundInstance instance) {
        SoundInstance current = instance;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (current instanceof TtsSoundInstance tts) {
                return tts;
            }

            SoundInstance unwrapped = ClientCompat.unwrapMovingSound(current);
            current = unwrapped == current ? null : unwrapped;
        }
        return null;
    }
}
