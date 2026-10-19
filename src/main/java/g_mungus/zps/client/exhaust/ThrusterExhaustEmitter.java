package g_mungus.zps.client.exhaust;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that blasts thruster exhaust. It says what leaves its nozzle this tick through
 * {@link #getCurrentExhaust()}; calling {@link #tickExhaust()} from its client tick emits one batch
 * of it. Everything is in the block's own space, so an emitter on a
 * moving grid gives grid positions and {@link ExhaustParticles} places the exhaust in the world.
 *
 * <p>{@link #getLevel()} and {@link #getBlockPos()} are a block entity's own.
 */
public interface ThrusterExhaustEmitter {

    /**
     * One tick's exhaust.
     *
     * @param amount    the configured particles per tick emitted, as a multiple: 0 to 1 a throttle,
     *                  above 1 more particles, as for a wide nozzle to look as dense; at most 8
     * @param width     nozzle width, blocks: how wide an area the particles leave across
     * @param speed     exhaust speed leaving the nozzle, blocks per tick
     * @param position  where the exhaust leaves, in the block's space
     * @param direction unit direction the exhaust leaves in, in the block's space
     * @param color     the colour of the fire, every colour cooling to the same smoke; COLD is
     *                  cold gas, a pale puff with no fire or glow
     */
    record ExhaustParams(
            float amount,
            float width,
            float speed,
            Vec3 position,
            Vec3 direction,
            ExhaustColor color
    ) {}

    enum ExhaustColor{
        ORANGE, BLUE, COLD
    }

    @Nullable Level getLevel();

    BlockPos getBlockPos();

    /** What leaves the nozzle this tick: null, or an amount of 0 or less, when not firing. */
    @Nullable ExhaustParams getCurrentExhaust();

    /** Emits this tick's batch if exhausting. Client side only; does nothing elsewhere. */
    default void tickExhaust() {
        if (!(getLevel() instanceof ClientLevel level) || !ExhaustParticles.available(level)) {
            return;
        }
        ExhaustParams exhaust = getCurrentExhaust();
        if (exhaust == null || exhaust.amount() <= 0) {
            return;
        }
        ExhaustParticles.emit(level, getBlockPos(), exhaust);
    }
}
