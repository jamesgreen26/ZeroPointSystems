package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.cableNetwork.core.CableNetworkComponent;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.util.INodeBlock;

/**
 * Base for blocks that are a node on the Kelvin gas network. The block owns the node's lifecycle —
 * created when placed, dropped when removed — while its block entity owns serialization and edge
 * ownership.
 *
 * <p>It is also the connection contract: a gas edge forms between two of these by mutual agreement,
 * deliberately parallel to {@link CableNetworkComponent}, which already does this for the cable
 * network. A connection forms only when both sides agree, and only within a shared standard. The
 * difference is that a gas connection is not merely on or off — the two sides also agree on
 * <em>what kind</em> of edge it is, so a pump or a valve can impose its behaviour on a connection it
 * shares with an ordinary neighbour.
 *
 * <p>That agreement is what Kelvin lacks: {@link INodeBlock#canConnectTo} can only answer yes or no,
 * with no way to say what the connection should do. Clockwork works around that by making its duct
 * block the sole author of every edge; here any two of these can agree directly, so a vent bolted
 * straight onto a vaporizer connects with no duct in between.
 */
public abstract class GasNodeBlock extends Block implements INodeBlock {

    /*
     * The per-face connection state every gas block carries. Both sides of a face read them during
     * negotiation, and DuctGeometry maps them to collision shapes.
     */
    public static final EnumProperty<DuctConnectionType> NORTH_CONNECTION = EnumProperty.create("north", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> SOUTH_CONNECTION = EnumProperty.create("south", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> EAST_CONNECTION = EnumProperty.create("east", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> WEST_CONNECTION = EnumProperty.create("west", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> UP_CONNECTION = EnumProperty.create("up", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> DOWN_CONNECTION = EnumProperty.create("down", DuctConnectionType.class);

    public GasNodeBlock(Properties properties) {
        super(properties);
    }

    // --- connection contract -----------------------------------------------------------------

    /** Only blocks sharing a standard connect. */
    public String getDuctStandard() {
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
    public abstract @Nullable GasEdgeProposal proposeEdge(BlockGetter level, BlockPos self, Direction toNeighbor);

    /**
     * The state this block should take given its neighbours, or null to leave it alone.
     */
    public abstract @Nullable BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos);

    /**
     * Kelvin's only cross-mod connection contract. Answering it from the same proposal the
     * handshake uses means other mods — Clockwork checks both sides before creating an edge — see
     * exactly the faces we would accept.
     */
    @Override
    public boolean canConnectTo(@NotNull BlockPos self, @NotNull BlockPos other,
                                @NotNull Direction direction, @NotNull BlockGetter level) {
        return proposeEdge(level, self, direction) != null;
    }

    // --- node lifecycle ----------------------------------------------------------------------

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
}
