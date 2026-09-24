package g_mungus.zps.block.gas.core.facets;

import g_mungus.zps.block.gas.core.CompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeFacet;
import g_mungus.zps.block.gas.core.PumpCompositeDuctEdge;
import net.minecraft.core.Direction;

/**
 * Drives gas along the connection. {@code pressure} is the pump's contribution in Pascals, pushing
 * toward {@code pushDirection}.
 *
 * <p>Two pumps on one connection add up as signed pressures along a shared reference axis: pumps
 * agreeing reinforce each other, pumps opposing partially cancel and the stronger one wins by the
 * difference. To keep the merge commutative, both are normalised onto the positive direction of
 * their shared axis before being summed.
 */
public record PumpFacet(double pressure, Direction pushDirection) implements GasEdgeFacet<PumpFacet> {

    public static final int ORDER = 200;

    public PumpFacet {
        // A negative pressure is just a pump facing the other way; normalise so equal facets
        // compare equal.
        if (pressure < 0) {
            pressure = -pressure;
            pushDirection = pushDirection.getOpposite();
        }
    }

    @Override
    public PumpFacet mergeWith(PumpFacet other) {
        Direction reference = positiveOf(pushDirection);
        double sum = signedAlong(reference) + other.signedAlong(reference);

        if (sum >= 0) {
            return new PumpFacet(sum, reference);
        }
        return new PumpFacet(-sum, reference.getOpposite());
    }

    /** This facet's pressure as a signed value along {@code reference}. */
    private double signedAlong(Direction reference) {
        if (pushDirection == reference) {
            return pressure;
        }
        if (pushDirection == reference.getOpposite()) {
            return -pressure;
        }
        // Facets on different axes cannot be merged; the negotiator only ever merges facets from
        // the two blocks sharing one face, which are always on the same axis.
        throw new IllegalArgumentException("Cannot merge pump facets on different axes: "
                + pushDirection + " and " + reference);
    }

    /** The positive-going direction of a direction's axis, so both sides pick the same reference. */
    private static Direction positiveOf(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? direction
                : direction.getOpposite();
    }

    @Override
    public void applyTo(CompositeDuctEdge edge, Direction aToB) {
        if (edge instanceof PumpCompositeDuctEdge pump) {
            pump.setPumpPressure(pressure);
            pump.setTarget(pushDirection == aToB ? pump.getNodeB() : pump.getNodeA());
        }
    }

    @Override
    public int applyOrder() {
        return ORDER;
    }
}
