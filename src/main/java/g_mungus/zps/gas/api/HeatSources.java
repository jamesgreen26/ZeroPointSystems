package g_mungus.zps.gas.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers {@link HeatSourceProvider}s, whose {@link HeatSource}s heat biomes using the
 * {@code zps:space} temperature modifier (see {@link g_mungus.zps.world.SpaceTemperature}). Other
 * biomes are unaffected. Register once, during mod construction or common setup; providers are used
 * on both sides.
 */
public final class HeatSources {

    private static final List<HeatSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<HeatSourceProvider> VIEW = Collections.unmodifiableList(PROVIDERS);

    private HeatSources() {
    }

    public static void register(HeatSourceProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void unregister(HeatSourceProvider provider) {
        PROVIDERS.remove(provider);
    }

    /** The registered providers, read-only. Iterating it is safe while providers are (un)registered. */
    public static List<HeatSourceProvider> providers() {
        return VIEW;
    }
}
