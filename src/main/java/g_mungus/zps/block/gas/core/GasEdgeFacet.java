package g_mungus.zps.block.gas.core;

import net.minecraft.core.Direction;

/**
 * One negotiable behaviour that a block wants a gas edge to have — pumping, one-way flow,
 * a restriction, and so on.
 *
 * <p>Facets are always expressed in <em>world</em> terms (a {@link Direction}, never
 * {@code nodeA}/{@code nodeB}), so that both blocks sharing a face compute the same merged result
 * regardless of which one runs the negotiation. {@link #mergeWith} must therefore be commutative:
 * {@code a.mergeWith(b)} and {@code b.mergeWith(a)} must be equal.
 *
 * <p>Facets of different types simply coexist on the finished edge — a pump facing a valve yields
 * an edge that both pumps and restricts. Only same-type facets are merged.
 *
 * @param <T> the concrete facet type, so merging stays type-safe
 */
public interface GasEdgeFacet<T extends GasEdgeFacet<T>> {

    /**
     * Combine this facet with another of the same type. Must be commutative.
     */
    T mergeWith(T other);

    /**
     * Write this facet's behaviour into the edge being built.
     *
     * @param edge the edge under construction
     * @param aToB the world direction pointing from the edge's {@code nodeA} to its {@code nodeB}
     */
    void applyTo(CompositeDuctEdge edge, Direction aToB);

    /**
     * Lower values apply first. Fixed so that the finished edge does not depend on facet ordering;
     * a blocked one-way must land after any aperture facet, since it wins by closing the aperture.
     */
    int applyOrder();
}
