package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.cableNetwork.core.CableNetworkComponent;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.INodeBlock;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

/**
 * Owns every gas edge mutation ZPS makes.
 *
 * <p>Three rules keep this safe against Kelvin's edge bookkeeping:
 *
 * <ol>
 *   <li><b>Canonical ordering.</b> Kelvin stores edges under {@code Pair(nodeA, nodeB)} but looks
 *       them up in either order, so two blocks adding the same pair in opposite orders would leave
 *       two entries for one connection. Node order is therefore always derived from position, never
 *       from whoever ran first — which costs nothing, because direction lives in the edge's own
 *       {@code reversed} / {@code target} fields rather than in the node order.
 *   <li><b>Compare before writing.</b> Kelvin's {@code addEdge} only dedupes on
 *       {@link ConnectionType}, which cannot tell two composites apart, so we compare against the
 *       live edge ourselves and remove before adding.
 *   <li><b>Clean up explicitly.</b> Kelvin's {@code removeNode} leaves a node's edges behind, so a
 *       block being broken tears down its own edges first.
 * </ol>
 */
public final class GasEdgeNegotiator {

    private GasEdgeNegotiator() {
    }

    /** An edge's two nodes in canonical order. */
    public record EdgeKey(DuctNodePos a, DuctNodePos b) {
    }

    // --- entry points ----------------------------------------------------------------------

    /**
     * Renegotiate this block and all six neighbours, the same shape as
     * {@link CableNetworkComponent#updateSelfAndNeighbors}.
     */
    public static void updateSelfAndNeighbors(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        updateConnections(level, pos);
        for (Direction direction : Direction.values()) {
            updateConnections(level, pos.relative(direction));
        }
    }

    /** Renegotiate every face of a single block. */
    public static void updateConnections(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GasNodeBlock self)) {
            return;
        }

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos selfNode = nodePos(level, pos);
        if (kelvin.getNodeAt(selfNode) == null) {
            // Our own node is not registered yet; the deferred pass after load will retry.
            return;
        }

        for (Direction direction : Direction.values()) {
            updateFace(level, kelvin, self, pos, selfNode, direction);
        }

        refreshConnectionState(level, pos, state);
    }

    /**
     * Bring a block's visible connection state in line with what it is actually joined to.
     *
     * <p>Needed because a block only computes its own state when it is placed: without this, the
     * first of two neighbours would keep the state it had when it was alone. Mirrors what
     * {@link CableNetworkComponent#updateConnections} does for the cable network. Only writes when
     * something changed, so the neighbour updates this triggers settle instead of recurring.
     */
    private static void refreshConnectionState(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof GasNodeBlock block)) {
            return;
        }
        BlockState updated = block.getConnectedState(level, state, pos);
        if (updated != null && !updated.equals(state)) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    /**
     * Drop every edge this block authored. Called before the node goes away, because Kelvin's
     * {@code removeNode} would otherwise leave the edges dangling.
     */
    public static void removeAllEdges(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        GasNodeBlockEntity blockEntity = gasNodeAt(level, pos);
        if (blockEntity == null) {
            return;
        }

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos selfNode = nodePos(level, pos);

        for (Direction direction : Direction.values()) {
            if (!blockEntity.hasAuthoredEdge(direction)) {
                continue;
            }
            EdgeKey key = canonical(selfNode, nodePos(level, pos.relative(direction)));
            kelvin.removeEdge(key.a(), key.b());
            blockEntity.setAuthoredEdge(direction, false);
        }
    }

    // --- per-face negotiation --------------------------------------------------------------

    private static void updateFace(Level level, DuctNetwork<?> kelvin, GasNodeBlock self,
                                   BlockPos pos, DuctNodePos selfNode, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        GasEdgeProposal selfProposal = self.proposeEdge(level, pos, direction);
        if (selfProposal == null) {
            dropEdge(level, kelvin, pos, selfNode, direction);
            return;
        }

        // Another mod's gas block that manages its own connections: stand well clear. We neither
        // create nor remove anything, so it is free to author the edge on its own terms.
        if (ZPSConfig.authorsOwnGasEdges(BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()))) {
            return;
        }

        GasEdgeProposal merged;
        if (neighborState.getBlock() instanceof GasNodeBlock neighbor) {
            if (!neighbor.getDuctStandard().equals(self.getDuctStandard())) {
                dropEdge(level, kelvin, pos, selfNode, direction);
                return;
            }
            GasEdgeProposal neighborProposal =
                    neighbor.proposeEdge(level, neighborPos, direction.getOpposite());
            if (neighborProposal == null) {
                dropEdge(level, kelvin, pos, selfNode, direction);
                return;
            }
            merged = selfProposal.mergeWith(neighborProposal);
        } else if (neighborState.getBlock() instanceof INodeBlock foreign) {
            // A foreign Kelvin node that does not author edges. Kelvin's only cross-mod contract is
            // canConnectTo, so honour it and build an ordinary pipe, counting our half-length twice
            // since the other side cannot tell us its own.
            if (!foreign.canConnectTo(neighborPos, pos, direction.getOpposite(), level)) {
                dropEdge(level, kelvin, pos, selfNode, direction);
                return;
            }
            merged = GasEdgeProposal.pipe(selfProposal.maxRadius(), selfProposal.halfLength() * 2);
        } else {
            dropEdge(level, kelvin, pos, selfNode, direction);
            return;
        }

        DuctNodePos neighborNode = nodePos(level, neighborPos);
        if (kelvin.getNodeAt(neighborNode) == null) {
            dropEdge(level, kelvin, pos, selfNode, direction);
            return;
        }

        writeEdge(level, kelvin, pos, selfNode, neighborNode, direction, merged);
    }

    private static void writeEdge(Level level, DuctNetwork<?> kelvin, BlockPos pos,
                                  DuctNodePos selfNode, DuctNodePos neighborNode,
                                  Direction direction, GasEdgeProposal merged) {
        EdgeKey key = canonical(selfNode, neighborNode);
        Direction aToB = key.a().equals(selfNode) ? direction : direction.getOpposite();
        CompositeDuctEdge desired = merged.buildEdge(key.a(), key.b(), aToB);

        DuctEdge existing = kelvin.getEdgeBetween(key.a(), key.b());
        if (existing != null && desired.matches(existing)) {
            markAuthored(level, pos, direction);
            return;
        }

        // Never rely on Kelvin's type-only dedup, and never leave the reversed key behind.
        kelvin.removeEdge(key.a(), key.b());
        kelvin.addEdge(key.a(), key.b(), desired);
        markAuthored(level, pos, direction);
    }

    private static void dropEdge(Level level, DuctNetwork<?> kelvin, BlockPos pos,
                                 DuctNodePos selfNode, Direction direction) {
        GasNodeBlockEntity blockEntity = gasNodeAt(level, pos);
        if (blockEntity == null || !blockEntity.hasAuthoredEdge(direction)) {
            return;
        }
        EdgeKey key = canonical(selfNode, nodePos(level, pos.relative(direction)));
        kelvin.removeEdge(key.a(), key.b());
        blockEntity.setAuthoredEdge(direction, false);
    }

    // --- helpers ---------------------------------------------------------------------------

    /**
     * Order two nodes by position so both sides of a face agree on the key, regardless of which one
     * runs the negotiation.
     */
    public static EdgeKey canonical(DuctNodePos first, DuctNodePos second) {
        return compare(first, second) <= 0 ? new EdgeKey(first, second) : new EdgeKey(second, first);
    }

    private static int compare(DuctNodePos first, DuctNodePos second) {
        int dimension = first.getDimensionId().toString().compareTo(second.getDimensionId().toString());
        if (dimension != 0) {
            return dimension;
        }
        int x = Double.compare(first.getX(), second.getX());
        if (x != 0) {
            return x;
        }
        int y = Double.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        return Double.compare(first.getZ(), second.getZ());
    }

    public static DuctNodePos nodePos(Level level, BlockPos pos) {
        return KelvinExtensions.INSTANCE.toDuctNodePos(pos, level.dimension().location());
    }

    private static void markAuthored(Level level, BlockPos pos, Direction direction) {
        GasNodeBlockEntity blockEntity = gasNodeAt(level, pos);
        if (blockEntity != null) {
            blockEntity.setAuthoredEdge(direction, true);
        }
    }

    private static @Nullable GasNodeBlockEntity gasNodeAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GasNodeBlockEntity node ? node : null;
    }

    /**
     * Whether this face would form an edge, without touching the network. Used for the block's
     * connection state and to answer Kelvin's cross-mod {@code canConnectTo}.
     */
    public static boolean wouldConnect(BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockState(pos).getBlock() instanceof GasNodeBlock self)) {
            return false;
        }
        if (self.proposeEdge(level, pos, direction) == null) {
            return false;
        }

        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof GasNodeBlock neighbor) {
            return neighbor.getDuctStandard().equals(self.getDuctStandard())
                    && neighbor.proposeEdge(level, neighborPos, direction.getOpposite()) != null;
        }
        if (neighborState.getBlock() instanceof INodeBlock foreign) {
            return foreign.canConnectTo(neighborPos, pos, direction.getOpposite(), level);
        }
        return false;
    }

    /** Schedules the deferred negotiation pass, once neighbouring nodes have had a chance to load. */
    public static void scheduleUpdate(Level level, BlockPos pos, Block block) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos, block, 1);
        }
    }
}
