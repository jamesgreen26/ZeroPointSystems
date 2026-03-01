package g_mungus.zps.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import g_mungus.zps.client.tts.TtsSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
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
    private CompletableFuture<SoundBuffer> redirectGetCompleteBuffer(SoundBufferLibrary library, net.minecraft.resources.ResourceLocation path, SoundInstance instance) {
        if (instance instanceof TtsSoundInstance tts) {
            // Convert the TTS byte array into a direct SoundBuffer (required by OpenAL)
            byte[] audioData = tts.getAudioData();
            ByteBuffer buffer = ByteBuffer.allocateDirect(audioData.length).put(audioData).flip();
            SoundBuffer soundBuffer = new SoundBuffer(buffer, tts.getFormat());
            return CompletableFuture.completedFuture(soundBuffer);
        }

        // fallback to normal behavior for other sounds
        return library.getCompleteBuffer(path);
    }
}