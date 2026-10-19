package g_mungus.zps.client.exhaust;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that blasts thruster exhaust. It says where the nozzle is, which way it points and
 * how fast the exhaust leaves; calling {@link #tickExhaust()} from its client tick emits one batch
 * while {@link #emitsExhaust()} holds. Everything is in the block's own space, so an emitter on a
 * moving grid gives grid positions and {@link ExhaustParticles} places the exhaust in the world.
 *
 * <p>{@link #getLevel()} and {@link #getBlockPos()} are a block entity's own.
 */
public interface ThrusterExhaustEmitter {

    @Nullable Level getLevel();

    BlockPos getBlockPos();

    /** Whether exhaust leaves the nozzle this tick. */
    boolean emitsExhaust();

    /** Where the exhaust leaves, in the block's space. */
    Vec3 exhaustPosition();

    /** Unit direction the exhaust leaves in, in the block's space. */
    Vec3 exhaustDirection();

    /** Exhaust speed leaving the nozzle, blocks per tick. */
    float exhaustSpeed();

    /** Emits this tick's batch if exhausting. Client side only; does nothing elsewhere. */
    default void tickExhaust() {
        if (!(getLevel() instanceof ClientLevel level) || !emitsExhaust() || !ExhaustParticles.available(level)) {
            return;
        }
        ExhaustParticles.emit(level, getBlockPos(), exhaustPosition(), exhaustDirection(), exhaustSpeed());
    }
}
