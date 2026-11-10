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
 * A {@link CompositeDuctEdge} that holds certain gases back, in both directions. Kelvin's solver
 * checks each gas at whichever node is the source this step and moves only the ones that pass.
 *
 * <p>Kept separate because the solver keys off the interface: an empty blacklist is inert, but
 * every edge would pay for the check.
 */
public class FilteredCompositeDuctEdge extends CompositeDuctEdge implements FilteredEdge {

    private final EdgeGasFilter filter = new EdgeGasFilter();

    public FilteredCompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                                     double radius, double length) {
        super(type, nodeA, nodeB, radius, length);
    }

    public void setFilter(GasFilter gasFilter) {
        filter.set(gasFilter);
    }

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
                && other instanceof FilteredCompositeDuctEdge filtered
                && filtered.filter.sameAs(this.filter);
    }
}
