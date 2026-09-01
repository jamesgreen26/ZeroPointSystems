package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.cableNetwork.core.CableNetworkComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by blocks that can form a gas edge with a neighbour by mutual agreement.
 *
 * <p>Deliberately parallel to
 * {@link CableNetworkComponent}, which already does this for the cable network: a connection forms
 * only when both sides agree, and only within a shared standard. The difference is that a gas
 * connection is not merely on or off — the two sides also
 * agree on <em>what kind</em> of edge it is, so a pump or a valve can impose its behaviour on a
 * connection it shares with an ordinary neighbour.
 *
 * <p>This is what Kelvin lacks: {@code INodeBlock.canConnectTo} can only answer yes or no, with no
 * way to say what the connection should do. Clockwork works around that by making its duct block
 * the sole author of every edge; here any two components can agree directly, so a vent bolted
 * straight onto a vaporizer connects with no duct in between.
 */
public interface GasNetworkComponent {

    /** Only blocks sharing a standard connect. */
    default String getDuctStandard() {
        return BuiltinDuctStandards.DEFAULT;
    }

    /**
     * What this block wants on the given face, or null to refuse a connection there entirely.
     *
     * <p>Must be a pure function of world state: it is called from both sides of a face, and on
     * both blocks whenever either changes.
     *
     * @param toNeighbor direction from this block toward the neighbour
     */
    @Nullable
    GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor);
}
