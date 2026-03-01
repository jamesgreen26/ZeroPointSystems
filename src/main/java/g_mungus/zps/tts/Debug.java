package g_mungus.zps.tts;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.common.MinecraftForge;

public class Debug {

    public static void test1(SoundInstance arg, SoundBufferLibrary soundBuffers, SoundEngine engine, Sound sound, boolean flag2, ChannelAccess.ChannelHandle handle) {
        arg.getStream(soundBuffers, sound, flag2).thenAccept(argx -> {
            System.out.println(1 + sound.getLocation().getPath());
            handle.execute(arg2 -> {
                System.out.println(2 + sound.getLocation().getPath());
                try {
                    arg2.attachBufferStream(argx);
                    System.out.println(3 + sound.getLocation().getPath());
                    arg2.play();
                    System.out.println(4 + sound.getLocation().getPath());
                    MinecraftForge.EVENT_BUS.post(new PlayStreamingSourceEvent(engine, arg, arg2));
                } catch (Throwable t) {
                    System.out.println(5 + sound.getLocation().getPath());
                    t.printStackTrace();
                }
            });
        });
    }
}
