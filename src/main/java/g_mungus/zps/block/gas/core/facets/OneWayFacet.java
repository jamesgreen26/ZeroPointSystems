package g_mungus.zps.block.gas.core.facets;

import g_mungus.zps.block.gas.core.CompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeFacet;
import g_mungus.zps.block.gas.core.OneWayCompositeDuctEdge;
import net.minecraft.core.Direction;

/**
 * Allows gas to flow only toward {@code allowedDirection}.
 *
 * <p>Two check valves pointing the same way are still one check valve. Two pointing at each other
 * pass nothing at all, which is what {@code blocked} represents — it is applied by closing the
 * aperture, so Kelvin's solver needs no special case.
 */
public record OneWayFacet(Direction allowedDirection, boolean blocked) implements GasEdgeFacet<OneWayFacet> {

    public static final int ORDER = 300;

    public OneWayFacet(Direction allowedDirection) {
        this(allowedDirection, false);
    }

    @Override
    public OneWayFacet mergeWith(OneWayFacet other) {
        if (this.blocked || other.blocked || this.allowedDirection != other.allowedDirection) {
            return new OneWayFacet(this.allowedDirection, true);
        }
        return this;
    }

    @Override
    public void applyTo(CompositeDuctEdge edge, Direction aToB) {
        if (blocked) {
            // Shut the connection: Kelvin blocks flow once radius + aperture reaches zero.
            edge.setBlocked(true);
            edge.setAperture(-edge.getRadius());
            return;
        }
        // Kelvin reads direction off the edge itself, independently of node ordering:
        // reversed == false means flow is permitted from nodeA to nodeB.
        if (edge instanceof OneWayCompositeDuctEdge oneWay) {
            oneWay.setReversed(allowedDirection != aToB);
        }
    }

    @Override
    public int applyOrder() {
        return ORDER;
    }
}
