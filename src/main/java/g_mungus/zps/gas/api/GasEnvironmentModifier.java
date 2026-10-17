package g_mungus.zps.gas.api;

/** A change to an entity's {@link GasEnvironment}, queued by a source with {@link GasEnvironments#queue}. */
@FunctionalInterface
public interface GasEnvironmentModifier {
    void modify(GasEnvironment environment);
}
