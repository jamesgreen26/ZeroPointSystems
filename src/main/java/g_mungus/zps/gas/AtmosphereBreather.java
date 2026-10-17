package g_mungus.zps.gas;

/** Implemented on {@code LivingEntity} by {@code LivingEntityMixin}: its drowning in gas, for {@link GasExposure}. */
public interface AtmosphereBreather {
    /** When {@link GasExposure#expose} last said it was drowning. */
    long zps$getDrowningTick();

    void zps$setDrowningTick(long gameTime);

    /** Whether its client has been told it is drowning: last sent on the server, last received on the client. */
    boolean zps$isSyncedDrowning();

    void zps$setSyncedDrowning(boolean drowning);
}
