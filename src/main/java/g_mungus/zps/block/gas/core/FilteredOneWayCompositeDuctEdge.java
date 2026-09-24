package g_mungus.zps.block.gas.core;

import g_mungus.zps.gas.GasFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.edges.FilteredEdge;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link OneWayCompositeDuctEdge} that also holds certain gases back. Kelvin's solver checks
 * each gas at the source against the filter and moves only the ones that pass. Kelvin's interface
 * allows a whitelist too; ZPS only ever sets a blacklist, but the flag is kept and persisted so
 * the edge stays faithful to what it was handed.
 *
 * <p>Kept separate for the same reason the one-way edge is: the solver keys off the interface,
 * and although an empty blacklist is inert, every edge would pay for the check.
 */
public class FilteredOneWayCompositeDuctEdge extends OneWayCompositeDuctEdge implements FilteredEdge {

    private static final String FILTER_TAG = "Filter";
    private static final String BLACKLIST_TAG = "Blacklist";

    private final HashSet<GasType> filter = new HashSet<>();
    private boolean blacklist = true;

    public FilteredOneWayCompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                                           double radius, double length) {
        super(type, nodeA, nodeB, radius, length);
    }

    /** Hold back the gases a {@link GasFilter} names. */
    public void setFilter(GasFilter gasFilter) {
        modFilter(gasFilter.resolve(), true);
    }

    // --- FilteredEdge ----------------------------------------------------------------------

    @Override
    public @NotNull HashSet<GasType> getFilter() {
        return filter;
    }

    @Override
    public boolean getBlacklist() {
        return blacklist;
    }

    @Override
    public void setBlacklist(boolean blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public void modFilter(@NotNull HashSet<GasType> newFilter, boolean isBlacklist) {
        filter.clear();
        filter.addAll(newFilter);
        blacklist = isBlacklist;
    }

    // --- persistence -----------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag serialize(@NotNull CompoundTag tag) {
        super.serialize(tag);
        Set<ResourceLocation> ids = new HashSet<>();
        for (GasType gas : filter) {
            ids.add(gas.getResourceLocation());
        }
        tag.put(FILTER_TAG, new GasFilter(ids).save(new CompoundTag()));
        tag.putBoolean(BLACKLIST_TAG, blacklist);
        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag tag) {
        super.deserialize(tag);
        modFilter(GasFilter.load(tag.getCompound(FILTER_TAG)).resolve(),
                !tag.contains(BLACKLIST_TAG) || tag.getBoolean(BLACKLIST_TAG));
    }

    @Override
    public boolean matches(DuctEdge other) {
        return super.matches(other)
                && other instanceof FilteredOneWayCompositeDuctEdge filtered
                && filtered.blacklist == this.blacklist
                && filtered.filter.equals(this.filter);
    }
}
