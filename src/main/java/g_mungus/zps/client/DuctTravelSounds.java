package g_mungus.zps.client;

import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.util.List;

/**
 * Silence inside the ducts.
 *
 * <p>Between two vents a rider is nowhere: the screen is black and, on the server, they are
 * already parked at the far end of the run. Left alone the client would keep playing whatever
 * happens around either end — a door at the vent they left, a machine at the one ahead — and
 * neither belongs to someone crawling through a wall. So while the screen is on its way to black
 * or held there, every sound is dropped except the ones the journey itself makes: the vent's clank
 * and the footfalls along the duct. Sound returns with the picture, as the far vent fades in.
 *
 * <p>Music is the one thing let through. It is not in the world, and a track cut off half way and
 * restarted from nothing would be more of an intrusion than letting it play on.
 */
public final class DuctTravelSounds {

    /**
     * The sources that are in the world, stopped as a hop begins so that nothing already playing
     * carries on into the dark. Music is left alone for the reason above, and so is the master
     * bucket, which is where interface clicks and spoken text live.
     */
    private static final List<SoundSource> WORLD_SOURCES = List.of(
            SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.BLOCKS, SoundSource.HOSTILE,
            SoundSource.NEUTRAL, SoundSource.PLAYERS, SoundSource.RECORDS);

    /** Set while a sound of the journey's own is being started, so the mute lets it by. */
    private static boolean playingOwn;

    private DuctTravelSounds() {
    }

    /** Runs {@code play}, letting whatever it starts through the mute. */
    static void playOwn(Runnable play) {
        playingOwn = true;
        try {
            play.run();
        } finally {
            playingOwn = false;
        }
    }

    /**
     * Stops everything in the world that is already sounding. Called as a hop begins, before the
     * journey's own sounds start, so those are not caught up in it.
     */
    static void hush() {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        for (SoundSource source : WORLD_SOURCES) {
            sounds.stop(null, source);
        }
    }

    /**
     * Whether new sounds are being dropped: the local player is in a duct and on their way, rather
     * than sitting in a vent or fading back into one. A duct that has dropped out from under them
     * for a moment mid-hop still counts as being in one.
     */
    public static boolean isMuted() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && (player.getVehicle() instanceof DuctTravelEntity || DuctTravelFade.isAwaitingVehicle())
                && DuctTravelFade.isUnderway();
    }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || playingOwn || sound.getSource() == SoundSource.MUSIC) {
            return;
        }
        if (isMuted()) {
            event.setSound(null);
        }
    }
}
