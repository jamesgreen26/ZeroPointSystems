package g_mungus.zps.block.gas.core;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.edges.PumpEdge;

/**
 * A {@link CompositeDuctEdge} that also drives gas along itself.
 *
 * <p>Kept separate because Kelvin filters out any flow running against a {@code PumpEdge}'s
 * {@code target}, regardless of pump pressure — an edge that carried this interface without a pump
 * behind it would only ever let gas move one way. A pump is therefore inherently one-directional
 * in Kelvin, which is why a pump and a check valve pointing the same way need no extra handling.
 */
public class PumpCompositeDuctEdge extends CompositeDuctEdge implements PumpEdge {

    private double pumpPressure;
    private DuctNodePos target;

    public PumpCompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                                 double radius, double length) {
        super(type, nodeA, nodeB, radius, length);
        this.target = nodeB;
    }

    @Override
    public double getPumpPressure() {
        return pumpPressure;
    }

    @Override
    public void setPumpPressure(double pumpPressure) {
        this.pumpPressure = pumpPressure;
    }

    @Override
    public @NotNull DuctNodePos getTarget() {
        return target;
    }

    @Override
    public void setTarget(@NotNull DuctNodePos target) {
        this.target = target;
    }

    @Override
    public @NotNull CompoundTag serialize(@NotNull CompoundTag tag) {
        super.serialize(tag);
        tag.putDouble("PumpPressure", pumpPressure);
        tag.putBoolean("TargetIsB", target.equals(getNodeB()));
        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag tag) {
        super.deserialize(tag);
        pumpPressure = tag.getDouble("PumpPressure");
        target = tag.getBoolean("TargetIsB") ? getNodeB() : getNodeA();
    }

    @Override
    public boolean matches(DuctEdge other) {
        return super.matches(other)
                && other instanceof PumpCompositeDuctEdge pump
                && pump.pumpPressure == this.pumpPressure
                && pump.target.equals(this.target);
    }
}
