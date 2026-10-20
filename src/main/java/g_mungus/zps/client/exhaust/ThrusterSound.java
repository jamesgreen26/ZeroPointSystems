package g_mungus.zps.client.exhaust;

import g_mungus.zps.ModSounds;
import g_mungus.zps.compat.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The roar of one firing thruster, sounded from its nozzle. Started by {@link #tick} while the
 * emitter burns, it follows the burn every tick: louder and a little higher as the throttle opens,
 * and easing out to silence once it closes, when it ends.
 */
final class ThrusterSound extends AbstractTickableSoundInstance {

    private static final double START_DISTANCE = 30;
    private static final double END_DISTANCE = 40;
    /** Loudness at full burn. */
    private static final float FULL_VOLUME = 0.89f;
    private static final float IDLE_PITCH = 0.75f;
    private static final float FULL_PITCH = 1.05f;
    /** How far two thrusters' pitches may sit apart, so they do not phase against each other. */
    private static final float PITCH_SPREAD = 0.06f;
    /** Share of the way to the burn the loudness closes each tick. */
    private static final float SMOOTHING = 0.25f;
    private static final float SILENT = 0.002f;

    private static final Map<ThrusterExhaustEmitter, ThrusterSound> playing = new HashMap<>();

    private final ThrusterExhaustEmitter emitter;
    private final float pitchOffset;
    private float level;

    private ThrusterSound(ThrusterExhaustEmitter emitter) {
        super(ModSounds.THRUSTER.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.emitter = emitter;
        this.pitchOffset = (random.nextFloat() - 0.5f) * PITCH_SPREAD;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        follow();
    }

    /**
     * Once a client tick per emitter: starts its sound if it burns within earshot and has none.
     * Ending is the sound's own business, in {@link #tick()}.
     */
    static void tick(ThrusterExhaustEmitter emitter) {
        ThrusterSound sound = playing.get(emitter);
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager sounds = minecraft.getSoundManager();
        if (sound != null) {
            if (sound.isStopped() || !sounds.isActive(sound)) {
                playing.remove(emitter);
            } else {
                return;
            }
        }
        if (burn(emitter) <= 0 || !audible(minecraft)) {
            return;
        }
        if (worldPosition(emitter).distanceTo(minecraft.gameRenderer.getMainCamera().getPosition()) > START_DISTANCE) {
            return;
        }
        sound = new ThrusterSound(emitter);
        playing.put(emitter, sound);
        sounds.play(sound);
    }

    /** Forgets every thruster: the level they stood in is gone, and the engine stops its sounds with it. */
    static void clear() {
        playing.clear();
    }

    /** Starts quiet and swells, so the engine must not throw it away for being silent. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (emitter.isRemoved() || emitter.getLevel() != minecraft.level || !audible(minecraft)) {
            end();
            return;
        }
        follow();
        double distance = new Vec3(x, y, z).distanceTo(minecraft.gameRenderer.getMainCamera().getPosition());
        if (distance > END_DISTANCE || (level < SILENT && burn(emitter) <= 0)) {
            end();
        }
    }

    private void end() {
        stop();
        playing.remove(emitter, this);
    }

    private void follow() {
        level += (Mth.clamp(burn(emitter), 0f, 1f) - level) * SMOOTHING;
        volume = level * FULL_VOLUME;
        pitch = Mth.lerp(level, IDLE_PITCH, FULL_PITCH) + pitchOffset;

        // Asked every tick: a thruster on a moving grid takes its nozzle with it.
        Vec3 at = worldPosition(emitter);
        x = at.x;
        y = at.y;
        z = at.z;
    }

    private static float burn(ThrusterExhaustEmitter emitter) {
        ThrusterExhaustEmitter.ExhaustParams exhaust = emitter.getCurrentExhaust();
        return exhaust == null ? 0f : exhaust.amount();
    }

    private static Vec3 worldPosition(ThrusterExhaustEmitter emitter) {
        return ClientCompat.toWorldRenderPos(emitter.getLevel(), emitter.getBlockPos().getCenter());
    }

    /**
     * Whether block sounds can be heard at all: the engine drops a muted channel without a word
     * and would never bring it back.
     */
    private static boolean audible(Minecraft minecraft) {
        return minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0f
                && minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) > 0f;
    }
}
