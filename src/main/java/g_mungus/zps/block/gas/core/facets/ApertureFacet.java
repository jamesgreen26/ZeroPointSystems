package g_mungus.zps.block.gas.core.facets;

import g_mungus.zps.block.gas.core.CompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeFacet;
import net.minecraft.core.Direction;

/**
 * Constricts the connection. Kelvin adds the aperture to the edge radius, so a negative aperture
 * narrows the connection and an aperture at or below {@code -radius} blocks it entirely.
 *
 * <p>Merging two apertures keeps the tighter one: a valve half-closed against a valve fully closed
 * is fully closed.
 */
public record ApertureFacet(double aperture) implements GasEdgeFacet<ApertureFacet> {

    public static final int ORDER = 100;

    @Override
    public ApertureFacet mergeWith(ApertureFacet other) {
        return new ApertureFacet(Math.min(this.aperture, other.aperture));
    }

    @Override
    public void applyTo(CompositeDuctEdge edge, Direction aToB) {
        edge.setAperture(aperture);
    }

    @Override
    public int applyOrder() {
        return ORDER;
    }
}
