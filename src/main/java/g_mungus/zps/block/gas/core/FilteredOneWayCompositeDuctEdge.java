package g_mungus.zps.block.gas.core;

import g_mungus.zps.gas.GasFilter;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.edges.FilteredEdge;

import java.util.HashSet;

/**
 * A {@link OneWayCompositeDuctEdge} that also holds certain gases back. Kelvin's solver checks
 * each gas at the source against the filter and moves only the ones that pass.
 *
 * <p>Kept separate for the same reason the one-way edge is: the solver keys off the interface,
 * and although an empty blacklist is inert, every edge would pay for the check.
 */
public class FilteredOneWayCompositeDuctEdge extends OneWayCompositeDuctEdge implements FilteredEdge {

    private final EdgeGasFilter filter = new EdgeGasFilter();

    public FilteredOneWayCompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                                           double radius, double length) {
        super(type, nodeA, nodeB, radius, length);
    }

    /** Hold back the gases a {@link GasFilter} names. */
    public void setFilter(GasFilter gasFilter) {
        filter.set(gasFilter);
    }

    // --- FilteredEdge ----------------------------------------------------------------------

    @Override
    public @NotNull HashSet<GasType> getFilter() {
        return filter.gases();
    }

    @Override
    public boolean getBlacklist() {
        return filter.blacklist();
    }

    @Override
    public void setBlacklist(boolean blacklist) {
        filter.setBlacklist(blacklist);
    }

    @Override
    public void modFilter(@NotNull HashSet<GasType> newFilter, boolean isBlacklist) {
        filter.set(newFilter, isBlacklist);
    }

    // --- persistence -----------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag serialize(@NotNull CompoundTag tag) {
        super.serialize(tag);
        filter.save(tag);
        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag tag) {
        super.deserialize(tag);
        filter.load(tag);
    }

    @Override
    public boolean matches(DuctEdge other) {
        return super.matches(other)
                && other instanceof FilteredOneWayCompositeDuctEdge filtered
                && filtered.filter.sameAs(this.filter);
    }
}
