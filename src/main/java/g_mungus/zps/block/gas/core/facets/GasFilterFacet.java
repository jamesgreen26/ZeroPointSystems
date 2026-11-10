package g_mungus.zps.block.gas.core.facets;

import g_mungus.zps.block.gas.core.CompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeFacet;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.kelvin.api.edges.FilteredEdge;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds the gases a {@link GasFilter} names back, whichever way the connection carries gas.
 *
 * <p>Two filters on one connection hold back everything either of them names.
 */
public record GasFilterFacet(GasFilter filter) implements GasEdgeFacet<GasFilterFacet> {

    public static final int ORDER = 250;

    @Override
    public GasFilterFacet mergeWith(GasFilterFacet other) {
        Set<ResourceLocation> union = new HashSet<>(filter.blocked());
        union.addAll(other.filter.blocked());
        return new GasFilterFacet(new GasFilter(union));
    }

    /** Whether any blocked gas is actually registered, so the edge has something to hold back. */
    public boolean active() {
        return !filter.resolve().isEmpty();
    }

    @Override
    public void applyTo(CompositeDuctEdge edge, Direction aToB) {
        if (edge instanceof FilteredEdge filtered) {
            filtered.modFilter(filter.resolve(), true);
        }
    }

    @Override
    public int applyOrder() {
        return ORDER;
    }
}
