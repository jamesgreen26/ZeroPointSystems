package g_mungus.zps.client.reactor;

import g_mungus.zps.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The hum of one hot reactor, sounded from the middle of its cavity. Looped for as long as the
 * {@link ClientReactor} keeps it, and followed every tick: louder and higher as the chamber heats.
 */
final class ReactorHumSound extends AbstractTickableSoundInstance {

    /** Loudness at ignition; a warming chamber climbs to this. */
    private static final float IGNITION_VOLUME = 0.175f;
    /** Loudness gained on top of that by twice the ignition temperature. */
    private static final float OVERDRIVE_VOLUME = 0.2f;
    private static final float COLD_PITCH = 0.8f;
    private static final float HOT_PITCH = 1.05f;
    /** How far two reactors' pitches may sit apart, so they do not phase against each other. */
    private static final float PITCH_SPREAD = 0.04f;

    private final ClientReactor reactor;

    ReactorHumSound(ClientReactor reactor) {
        super(ModSounds.REACTOR_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.reactor = reactor;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        follow();
    }

    /** Starts quiet and swells, so the engine must not throw it away for being silent. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        follow();
    }

    /** Ends the loop; the sound engine drops it on its next tick. */
    void end() {
        stop();
    }

    private void follow() {
        float heat = reactor.displayHeat();
        float toIgnition = Mth.clamp(heat, 0f, 1f);
        float pastIgnition = Mth.clamp(heat - 1f, 0f, 1f);
        volume = toIgnition * IGNITION_VOLUME + pastIgnition * OVERDRIVE_VOLUME;
        pitch = Mth.lerp(Mth.clamp(heat, 0f, 2f) / 2f, COLD_PITCH, HOT_PITCH) + (reactor.seed() - 0.5f) * PITCH_SPREAD;

        // Asked every tick: a reactor on a moving grid takes its centre with it.
        Vec3 centre = reactor.centre();
        x = centre.x;
        y = centre.y;
        z = centre.z;
    }
}
