package g_mungus.zps.block.gas.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.edges.ApertureEdge;

/**
 * A gas edge assembled from the facets both neighbours asked for.
 *
 * <p>Kelvin's solver reads behaviour off an edge with a series of independent {@code instanceof}
 * checks — pump pressure, aperture, one-way clamping — and applies each to the same flow
 * calculation. So one edge implementing several of those interfaces gets all of the behaviours,
 * which is what lets a pump and a valve share a single connection.
 *
 * <p>That cuts both ways: the solver keys off the interfaces an edge implements, not its values.
 * Any edge implementing {@code OneWayEdge} gets the one-way clamp, and any edge implementing
 * {@code PumpEdge} has backflow toward its {@code target} filtered out — whatever the pump pressure
 * is, even zero. Carrying either interface inert would silently make every connection
 * one-directional, so both live in subclasses used only when the matching facet is present:
 * {@link OneWayCompositeDuctEdge} and {@link PumpCompositeDuctEdge}. Only the aperture is safe to
 * carry inert, since a zero aperture genuinely leaves the flow untouched.
 */
public class CompositeDuctEdge implements DuctEdge, ApertureEdge {

    private final ConnectionType type;
    private final DuctNodePos nodeA;
    private final DuctNodePos nodeB;

    private double radius;
    private double length;
    private double currentFlowRate;
    private boolean unloaded;

    private double aperture;

    /** Set when merged facets cancel out entirely, e.g. two check valves pointing at each other. */
    private boolean blocked;

    public CompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                             double radius, double length) {
        this.type = type;
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.radius = radius;
        this.length = length;
    }

    // --- composite state -------------------------------------------------------------------

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    // --- DuctEdge --------------------------------------------------------------------------

    @Override
    public @NotNull ConnectionType getType() {
        return type;
    }

    @Override
    public @NotNull DuctNodePos getNodeA() {
        return nodeA;
    }

    @Override
    public @NotNull DuctNodePos getNodeB() {
        return nodeB;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    @Override
    public void setRadius(double radius) {
        this.radius = radius;
    }

    @Override
    public double getLength() {
        return length;
    }

    @Override
    public void setLength(double length) {
        this.length = length;
    }

    @Override
    public double getCurrentFlowRate() {
        return currentFlowRate;
    }

    @Override
    public void setCurrentFlowRate(double currentFlowRate) {
        this.currentFlowRate = currentFlowRate;
    }

    @Override
    public boolean getUnloaded() {
        return unloaded;
    }

    @Override
    public void setUnloaded(boolean unloaded) {
        this.unloaded = unloaded;
    }

    @Override
    public boolean interact(@NotNull ServerPlayer player) {
        return false;
    }

    @Override
    public void markLoaded() {
        this.unloaded = false;
    }

    @Override
    public void markUnloaded() {
        this.unloaded = true;
    }

    // --- ApertureEdge ----------------------------------------------------------------------

    @Override
    public double getAperture() {
        return aperture;
    }

    @Override
    public void setAperture(double aperture) {
        this.aperture = aperture;
    }

    // --- persistence -----------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag serialize(@NotNull CompoundTag tag) {
        tag.putDouble("Radius", radius);
        tag.putDouble("Length", length);
        tag.putDouble("Aperture", aperture);
        tag.putBoolean("Blocked", blocked);
        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag tag) {
        radius = tag.getDouble("Radius");
        length = tag.getDouble("Length");
        aperture = tag.getDouble("Aperture");
        blocked = tag.getBoolean("Blocked");
    }

    /**
     * Whether this edge would behave identically to {@code other}, so the negotiator can leave an
     * existing edge alone instead of tearing it down and rebuilding it. Kelvin's own
     * {@code addEdge} only compares {@link ConnectionType}, which cannot tell two different
     * composites apart.
     */
    public boolean matches(DuctEdge other) {
        if (!(other instanceof CompositeDuctEdge composite) || other.getClass() != this.getClass()) {
            return false;
        }
        return composite.type == this.type
                && composite.nodeA.equals(this.nodeA)
                && composite.nodeB.equals(this.nodeB)
                && composite.radius == this.radius
                && composite.length == this.length
                && composite.aperture == this.aperture
                && composite.blocked == this.blocked;
    }
}
