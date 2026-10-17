package g_mungus.zps.gas;

import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.gas.api.GasEnvironmentModifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Implemented on {@code LivingEntity} by {@code LivingEntityMixin}: its drowning in gas, for {@link GasExposure}. */
public interface AtmosphereBreather {
    /** When {@link GasExposure#expose} last said it was drowning. */
    long zps$getDrowningTick();

    void zps$setDrowningTick(long gameTime);

    /** Whether its client has been told it is drowning: last sent on the server, last received on the client. */
    boolean zps$isSyncedDrowning();

    void zps$setSyncedDrowning(boolean drowning);

    /** Modifiers queued for its next {@code GasEnvironment}, by {@code GasEnvironments.queue}. */
    List<GasEnvironmentModifier> zps$getQueuedModifiers();

    /** When a modifier was last queued. */
    long zps$getQueuedTick();

    void zps$setQueuedTick(long gameTime);

    /** What was last applied to it, kept for players only. Server side. */
    @Nullable GasConditions zps$getGasConditions();

    void zps$setGasConditions(@Nullable GasConditions conditions);
}
