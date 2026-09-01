package g_mungus.zps.block.gas.core;

import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;

/**
 * Base for blocks that are a node on the Kelvin gas network. The block owns the node's lifecycle —
 * created when placed, dropped when removed — while its block entity owns serialization and edge
 * ownership.
 */
public abstract class KNodeBlockImpl extends Block implements KNodeBlock {

    public KNodeBlockImpl(Properties properties) {
        super(properties);
    }

    /**
     * The state this block should take given its neighbours, or null to leave it alone.
     */
    public abstract @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos);

    @Override
    protected void onPlace(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean moved
    ) {
        super.onPlace(state, level, pos, oldState, moved);
        nodePlace(state, level, pos, oldState, moved);

        BlockState newState = getConnectedState(level, state, pos);

        if (newState != null) {
            level.setBlockAndUpdate(pos, newState);
        }

        GasEdgeNegotiator.updateSelfAndNeighbors(level, pos);
    }

    @Override
    protected void onRemove(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState newState,
            boolean moved
    ) {
        boolean blockGoingAway = !newState.is(state.getBlock());

        if (blockGoingAway) {
            // Kelvin's removeNode leaves this node's edges behind, so drop them while the block
            // entity that recorded them is still here.
            GasEdgeNegotiator.removeAllEdges(level, pos);
        }

        nodeRemove(state, level, pos, newState, moved);
        super.onRemove(state, level, pos, newState, moved);

        if (blockGoingAway) {
            // Let the neighbours notice they have lost a partner.
            for (Direction direction : Direction.values()) {
                GasEdgeNegotiator.updateConnections(level, pos.relative(direction));
            }
        }
    }

    /** Runs the deferred negotiation pass scheduled after a block entity loads. */
    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                        @NotNull RandomSource random) {
        super.tick(state, level, pos, random);

        // A block entity that loaded from disk has no node until this runs.
        if (level.getBlockEntity(pos) instanceof GasNodeBlockEntity node) {
            node.restoreNodeIfMissing();

            if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node.getDuctNodePosition()) == null) {
                // Chunk may not have been ready. Try again rather than leaving the block orphaned.
                GasEdgeNegotiator.scheduleUpdate(level, pos, this);
                return;
            }
        }
        GasEdgeNegotiator.updateConnections(level, pos);
    }

    /**
     * Kelvin's only cross-mod connection contract. Answering it from the same proposal the
     * handshake uses means other mods — Clockwork checks both sides before creating an edge — see
     * exactly the faces we would accept.
     */
    @Override
    public boolean canConnectTo(@NotNull BlockPos self, @NotNull BlockPos other,
                                @NotNull Direction direction, @NotNull BlockGetter level) {
        if (this instanceof GasNetworkComponent component) {
            return component.proposeEdge(level, self, direction) != null;
        }
        return KNodeBlock.super.canConnectTo(self, other, direction, level);
    }
}
