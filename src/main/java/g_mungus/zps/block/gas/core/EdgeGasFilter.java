package g_mungus.zps.block.gas.core;

import g_mungus.zps.gas.GasFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.HashSet;
import java.util.Set;

/**
 * The state behind Kelvin's {@code FilteredEdge}, shared by every filtered composite edge so the
 * plain, one-way and pump variants hold, persist and compare it the same way. Kelvin's interface
 * allows a whitelist too; ZPS only ever sets a blacklist, but the flag is kept and persisted so
 * the edge stays faithful to what it was handed.
 */
public final class EdgeGasFilter {

    private static final String FILTER_TAG = "Filter";
    private static final String BLACKLIST_TAG = "Blacklist";

    private final HashSet<GasType> gases = new HashSet<>();
    private boolean blacklist = true;

    public HashSet<GasType> gases() {
        return gases;
    }

    public boolean blacklist() {
        return blacklist;
    }

    public void setBlacklist(boolean blacklist) {
        this.blacklist = blacklist;
    }

    public void set(HashSet<GasType> newGases, boolean isBlacklist) {
        gases.clear();
        gases.addAll(newGases);
        blacklist = isBlacklist;
    }

    /** Hold back the gases a {@link GasFilter} names. */
    public void set(GasFilter filter) {
        set(filter.resolve(), true);
    }

    public void save(CompoundTag tag) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (GasType gas : gases) {
            ids.add(gas.getResourceLocation());
        }
        tag.put(FILTER_TAG, new GasFilter(ids).save(new CompoundTag()));
        tag.putBoolean(BLACKLIST_TAG, blacklist);
    }

    public void load(CompoundTag tag) {
        set(GasFilter.load(tag.getCompound(FILTER_TAG)).resolve(),
                !tag.contains(BLACKLIST_TAG) || tag.getBoolean(BLACKLIST_TAG));
    }

    public boolean sameAs(EdgeGasFilter other) {
        return other.blacklist == blacklist && other.gases.equals(gases);
    }
}
