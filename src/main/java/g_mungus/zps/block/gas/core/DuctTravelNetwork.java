package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The vents a player can reach from the one they climbed into.
 *
 * <p>This is the gas network read as a transit map. It walks the same faces the gas does, using
 * {@link GasEdgeNegotiator#wouldConnect} as the single source of truth for whether two blocks are
 * joined — the same predicate the ducts draw their own connection state from, so a run that looks
 * joined always is. Nothing here touches Kelvin: the edges themselves are irrelevant to a player
 * walking the graph, and going through the block states keeps this usable before a chunk's nodes
 * have finished registering.
 *
 * <p>A vent joins the network on the face behind its grille and nowhere else, so a run naturally
 * dead-ends at the grille rather than running through it. That is what makes a vent an endpoint
 * without any special handling here.
 */
public final class DuctTravelNetwork {

    /** How many vents one duct run will offer. Beyond this the nearest ones win. */
    public static final int MAX_DESTINATIONS = 32;

    private DuctTravelNetwork() {
    }

    /**
     * Every vent reachable through duct from {@code startVent}, nearest first, with the starting
     * vent itself at index 0 so cycling always leads back to where the player came in.
     *
     * <p>Server side only.
     */
    public static List<BlockPos> reachableVents(Level level, BlockPos startVent) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();

        BlockPos start = startVent.immutable();
        visited.add(start);
        queue.add(start);

        int budget = ZPSConfig.ductTravelMaxNodes();

        while (!queue.isEmpty() && visited.size() < budget) {
            BlockPos current = queue.poll();

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                // A run that leaves loaded chunks simply stops here. Pulling chunks in to finish
                // the search would let any player load arbitrary terrain by climbing into a vent.
                if (!level.hasChunkAt(neighbor)) {
                    continue;
                }
                if (!GasEdgeNegotiator.wouldConnect(level, current, direction)) {
                    continue;
                }

                visited.add(neighbor);
                queue.add(neighbor);

                BlockState state = level.getBlockState(neighbor);
                // A shut vent still caps its run like any other, but it cannot be climbed out of.
                if (state.getBlock() instanceof VentBlock && !VentBlock.isShut(state)) {
                    found.add(neighbor.immutable());
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));

        List<BlockPos> destinations = new ArrayList<>();
        destinations.add(start);
        for (BlockPos vent : found) {
            if (destinations.size() > MAX_DESTINATIONS) {
                break;
            }
            destinations.add(vent);
        }
        return List.copyOf(destinations);
    }
}
