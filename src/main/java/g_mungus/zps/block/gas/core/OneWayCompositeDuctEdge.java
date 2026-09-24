package g_mungus.zps.block.gas.core;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.edges.OneWayEdge;

/**
 * A {@link CompositeDuctEdge} that also restricts flow to one direction.
 *
 * <p>Kept separate because Kelvin's solver clamps flow on every edge implementing
 * {@link OneWayEdge}, so this class is only used when a one-way facet is actually present.
 */
public class OneWayCompositeDuctEdge extends CompositeDuctEdge implements OneWayEdge {

    private boolean reversed;

    public OneWayCompositeDuctEdge(ConnectionType type, DuctNodePos nodeA, DuctNodePos nodeB,
                                   double radius, double length) {
        super(type, nodeA, nodeB, radius, length);
    }

    @Override
    public boolean getReversed() {
        return reversed;
    }

    @Override
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    @Override
    public @NotNull CompoundTag serialize(@NotNull CompoundTag tag) {
        super.serialize(tag);
        tag.putBoolean("Reversed", reversed);
        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag tag) {
        super.deserialize(tag);
        reversed = tag.getBoolean("Reversed");
    }

    @Override
    public boolean matches(DuctEdge other) {
        return super.matches(other)
                && other instanceof OneWayCompositeDuctEdge oneWay
                && oneWay.reversed == this.reversed;
    }
}
