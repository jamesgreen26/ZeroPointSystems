package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.gas.core.facets.ApertureFacet;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.block.gas.core.facets.PumpFacet;
import net.minecraft.core.Direction;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctNodePos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one block wants from the connection on one of its faces: the behaviours it contributes,
 * plus its share of the connection's geometry.
 *
 * <p>Two proposals — one from each side of a face — merge into the single edge that is handed to
 * Kelvin. The merge is commutative, so both blocks compute the same edge.
 */
public final class GasEdgeProposal {

    private final List<GasEdgeFacet<?>> facets;
    private final double maxRadius;
    private final double halfLength;

    private GasEdgeProposal(List<GasEdgeFacet<?>> facets, double maxRadius, double halfLength) {
        this.facets = facets;
        this.maxRadius = maxRadius;
        this.halfLength = halfLength;
    }

    /** A plain, unrestricted connection. */
    public static GasEdgeProposal pipe(double maxRadius, double halfLength) {
        return new GasEdgeProposal(List.of(), maxRadius, halfLength);
    }

    /** This proposal plus one more behaviour. */
    public GasEdgeProposal with(GasEdgeFacet<?> facet) {
        List<GasEdgeFacet<?>> combined = new ArrayList<>(facets);
        combined.add(facet);
        return new GasEdgeProposal(combined, maxRadius, halfLength);
    }

    public List<GasEdgeFacet<?>> facets() {
        return facets;
    }

    public double maxRadius() {
        return maxRadius;
    }

    public double halfLength() {
        return halfLength;
    }

    /**
     * Combine both sides of a face. The narrower radius wins, the two half-lengths add up, and
     * facets of the same type merge by their own rules; facets of different types simply coexist.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public GasEdgeProposal mergeWith(GasEdgeProposal other) {
        Map<Class<?>, GasEdgeFacet<?>> merged = new LinkedHashMap<>();

        for (GasEdgeFacet<?> facet : facets) {
            merged.put(facet.getClass(), facet);
        }
        for (GasEdgeFacet<?> facet : other.facets) {
            GasEdgeFacet<?> existing = merged.get(facet.getClass());
            if (existing == null) {
                merged.put(facet.getClass(), facet);
            } else {
                // Safe: the map is keyed by concrete facet class, so both sides are the same type.
                GasEdgeFacet<?> combined = (GasEdgeFacet<?>) ((GasEdgeFacet) existing).mergeWith(facet);
                merged.put(facet.getClass(), combined);
            }
        }

        return new GasEdgeProposal(
                new ArrayList<>(merged.values()),
                Math.min(this.maxRadius, other.maxRadius),
                this.halfLength + other.halfLength);
    }

    /**
     * Build the edge this proposal describes.
     *
     * @param nodeA the canonically-first node
     * @param nodeB the canonically-second node
     * @param aToB  the world direction pointing from {@code nodeA} to {@code nodeB}
     */
    public CompositeDuctEdge buildEdge(DuctNodePos nodeA, DuctNodePos nodeB, Direction aToB) {
        // Only carry an interface the edge genuinely needs: Kelvin applies pump and one-way
        // behaviour on the strength of the interface alone, whatever the values behind it.
        CompositeDuctEdge edge;
        if (needsPump()) {
            edge = new PumpCompositeDuctEdge(connectionType(), nodeA, nodeB, maxRadius, halfLength);
        } else if (needsOneWay()) {
            edge = new OneWayCompositeDuctEdge(connectionType(), nodeA, nodeB, maxRadius, halfLength);
        } else {
            edge = new CompositeDuctEdge(connectionType(), nodeA, nodeB, maxRadius, halfLength);
        }

        List<GasEdgeFacet<?>> ordered = new ArrayList<>(facets);
        ordered.sort(Comparator.comparingInt(GasEdgeFacet::applyOrder));
        for (GasEdgeFacet<?> facet : ordered) {
            facet.applyTo(edge, aToB);
        }

        if (needsPump() && checkValveOpposesThePump()) {
            // A pump already blocks flow against itself, so a check valve pointing the same way is
            // redundant; one pointing the other way leaves nothing that can move.
            edge.setBlocked(true);
            edge.setAperture(-edge.getRadius());
        }
        return edge;
    }

    private boolean needsPump() {
        return facets.stream().anyMatch(PumpFacet.class::isInstance);
    }

    private boolean checkValveOpposesThePump() {
        Direction pumping = facets.stream()
                .filter(PumpFacet.class::isInstance)
                .map(f -> ((PumpFacet) f).pushDirection())
                .findFirst().orElse(null);
        return pumping != null && facets.stream()
                .anyMatch(f -> f instanceof OneWayFacet oneWay
                        && (oneWay.blocked() || oneWay.allowedDirection() != pumping));
    }

    /**
     * Whether a live one-way restriction is present. A one-way facet that merged into a blocked
     * state does not count — it is applied by closing the aperture instead, so the edge does not
     * need to implement Kelvin's {@code OneWayEdge} at all.
     */
    private boolean needsOneWay() {
        return facets.stream().anyMatch(f -> f instanceof OneWayFacet oneWay && !oneWay.blocked());
    }

    private ConnectionType connectionType() {
        boolean pump = facets.stream().anyMatch(PumpFacet.class::isInstance);
        boolean aperture = facets.stream().anyMatch(ApertureFacet.class::isInstance);
        boolean blocked = facets.stream().anyMatch(f -> f instanceof OneWayFacet ow && ow.blocked());

        // Kelvin's ConnectionType has no pump entry, so anything with a pump is OTHER.
        if (pump) {
            return ConnectionType.OTHER;
        }
        if (needsOneWay()) {
            return aperture ? ConnectionType.APERTURE_ONEWAY : ConnectionType.ONEWAY;
        }
        if (aperture || blocked) {
            return ConnectionType.APERTURE;
        }
        return ConnectionType.PIPE;
    }
}
