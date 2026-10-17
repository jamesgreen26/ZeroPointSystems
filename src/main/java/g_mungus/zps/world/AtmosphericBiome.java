package g_mungus.zps.world;

/** Implemented on {@code Biome} by {@code BiomeMixin}. */
public interface AtmosphericBiome {
    /** This biome's {@link AtmosphericComposition}: plain air until the registries finish loading. */
    AtmosphericComposition zps$getAtmosphericComposition();

    /** Set by {@code RegistryDataLoaderMixin} once both this biome and its composition have loaded. */
    void zps$setAtmosphericComposition(AtmosphericComposition composition);
}
