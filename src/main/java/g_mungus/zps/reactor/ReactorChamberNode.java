package g_mungus.zps.reactor;

import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.HashSet;

/**
 * The Kelvin node for a reactor chamber: the whole cavity as one well-mixed parcel, with the
 * shell's thermal mass folded in.
 *
 * <p>Kelvin's own failure handling is switched off by giving the node no pressure or temperature
 * ceiling. The reactor decides when and how it fails, because it wants to pick a wall block, scale
 * the blast, and hand out advancements — none of which Kelvin's generic handling can do.
 */
public class ReactorChamberNode extends PipeDuctNode {

    private final int reactorId;

    public ReactorChamberNode(DuctNodePos pos, int reactorId, double volume, double wallHeatCapacity) {
        super(pos, NodeBehaviorType.PIPE, new HashSet<>(), volume, Double.MAX_VALUE, Double.MAX_VALUE,
                wallHeatCapacity);
        this.reactorId = reactorId;
    }

    public int getReactorId() {
        return reactorId;
    }
}
