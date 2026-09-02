package g_mungus.zps.blockentity.gas.core;

import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.networking.GasNodeSyncS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.util.INodeBlock;
import org.valkyrienskies.kelvin.util.INodeBlockEntity;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

import java.util.EnumSet;
import java.util.Map;

/**
 * Base for every block entity that is a node on the Kelvin gas network.
 *
 * <p>Handles the two things Kelvin does not: remembering which edges we authored, so they can be
 * torn down explicitly (Kelvin's {@code removeNode} leaves a node's edges behind), and rebuilding
 * connections after load (Kelvin persists gas masses and temperatures only — its chunk-level
 * network save/load is commented out upstream, so edges never survive a reload on their own).
 *
 * <p>Edge <em>settings</em> need no separate storage: an edge is derived entirely from what the two
 * blocks propose, so each machine persisting its own state — a valve's aperture, a pump's pressure —
 * is enough to reproduce the same edge. This is why we do not need Clockwork's parallel
 * {@code edgeData} map of serialized edges.
 */
public abstract class GasNodeBlockEntity extends BlockEntity implements INodeBlockEntity {

    /** How often node state is pushed to nearby clients, in ticks. */
    private static final int SYNC_INTERVAL = 10;

    private final EnumSet<Direction> authoredEdges = EnumSet.noneOf(Direction.class);

    private int syncCooldown;

    /**
     * Node data read from disk, held until there is a node to put it in.
     *
     * <p>Kelvin persists nothing itself — its chunk save and load are commented out upstream — and
     * its {@code addNode} resets a node's info, so data restored before the node exists would be
     * wiped the moment the node is rebuilt. Hence: stash first, rebuild, then apply.
     */
    private CompoundTag pendingNodeData;

    // The last figures actually put on the wire, kept so behaviour can be asserted against what
    // clients really receive rather than against whatever the node happens to hold right now.
    private double lastSentMass;
    private double lastSentPressure;

    // Client-side mirror of the simulated state, fed by GasNodeSyncS2CPacket.
    private double syncedGasMass;
    private double syncedPressure;
    private double syncedTemperature = 273.15;

    protected GasNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- edge ownership --------------------------------------------------------------------

    public boolean hasAuthoredEdge(Direction direction) {
        return authoredEdges.contains(direction);
    }

    public void setAuthoredEdge(Direction direction, boolean authored) {
        boolean changed = authored ? authoredEdges.add(direction) : authoredEdges.remove(direction);
        if (changed) {
            setChanged();
        }
    }

    // --- simulated state -------------------------------------------------------------------

    /** Total gas mass at this node, in kilograms. Reads the simulation on the server, the last
     *  synced value on the client. */
    public double getGasMass() {
        if (level == null || level.isClientSide()) {
            return syncedGasMass;
        }
        double total = 0;
        for (double mass : KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(getDuctNodePosition()).values()) {
            total += mass;
        }
        return total;
    }

    /** Pressure at this node, in Pascals. */
    public double getPressure() {
        if (level == null || level.isClientSide()) {
            return syncedPressure;
        }
        return KelvinMod.INSTANCE.forceGetKelvin().getPressureAt(getDuctNodePosition());
    }

    /** Temperature at this node, in Kelvin. */
    public double getTemperature() {
        if (level == null || level.isClientSide()) {
            return syncedTemperature;
        }
        return KelvinMod.INSTANCE.forceGetKelvin().getTemperatureAt(getDuctNodePosition());
    }

    /** The gases present at this node, by mass. Server only. */
    public Map<GasType, Double> getGases() {
        if (level == null || level.isClientSide()) {
            return Map.of();
        }
        return KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(getDuctNodePosition());
    }

    public void acceptSyncedState(double gasMass, double pressure, double temperature) {
        this.syncedGasMass = gasMass;
        this.syncedPressure = pressure;
        this.syncedTemperature = temperature;
    }

    /**
     * Push node state to nearby clients on a slow tick. Call from a server ticker; blocks that draw
     * gas — particles, gauges — need it, and it is the only way the client learns anything about
     * the simulation.
     *
     * @return true if a packet actually went out this tick, so a subclass can reset whatever it
     *         has been accumulating since the last one
     */
    protected boolean syncNodeState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        restoreNodeIfMissing();
        if (--syncCooldown > 0) {
            return false;
        }
        syncCooldown = SYNC_INTERVAL;

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        if (kelvin.getNodeAt(getDuctNodePosition()) == null) {
            return false;
        }

        lastSentMass = massForSync();
        lastSentPressure = pressureForSync();

        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(worldPosition),
                new GasNodeSyncS2CPacket(worldPosition, lastSentMass, lastSentPressure, getTemperature()));
        return true;
    }

    /**
     * The mass figure to send clients. Defaults to what is actually sitting at the node; a block
     * that empties its node every tick has nothing left to report and overrides this with its
     * throughput instead.
     */
    protected double massForSync() {
        return getGasMass();
    }

    /** The pressure figure to send clients. See {@link #massForSync()}. */
    protected double pressureForSync() {
        return getPressure();
    }

    /** The mass figure most recently sent to clients. */
    public double getLastSentMass() {
        return lastSentMass;
    }

    /** The pressure figure most recently sent to clients. */
    public double getLastSentPressure() {
        return lastSentPressure;
    }

    /** How often, in ticks, {@link #syncNodeState()} actually sends. */
    protected static int syncInterval() {
        return SYNC_INTERVAL;
    }

    // --- node lifecycle --------------------------------------------------------------------

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);

        if (level instanceof ServerLevel) {
            KelvinMod.INSTANCE.forceGetKelvin().markLoaded(getDuctNodePosition());
        }
    }

    /**
     * Put this block's node back into the network if it is missing, then apply whatever was saved
     * for it.
     *
     * <p>A node is only ever created when its block is placed, so after a reload the blocks are
     * all still there and the network is empty. Without this, every gas network dies on relog.
     */
    public void restoreNodeIfMissing() {
        if (level == null || level.isClientSide()) {
            return;
        }

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos nodePos = getDuctNodePosition();

        if (pendingNodeData == null && kelvin.getNodeAt(nodePos) != null) {
            return;
        }

        if (kelvin.getNodeAt(nodePos) == null
                && getBlockState().getBlock() instanceof INodeBlock nodeBlock) {
            nodeBlock.nodePlace(getBlockState(), level, worldPosition,
                    Blocks.AIR.defaultBlockState(), false);
        }

        if (pendingNodeData != null && kelvin.getNodeAt(nodePos) != null) {
            loadData(pendingNodeData, nodePos, false);
            pendingNodeData = null;
            kelvin.markLoaded(nodePos);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && !level.isClientSide()) {
            // Deferred by a tick: neighbouring block entities may not have registered their nodes
            // yet, and an edge needs both ends to exist.
            GasEdgeNegotiator.scheduleUpdate(level, worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        // The server-side node is dropped by the block's nodeRemove; only the client copy needs
        // clearing here.
        if (level != null && level.isClientSide()) {
            KelvinMod.INSTANCE.getClientKelvin().removeNode(getDuctNodePosition());
        }
    }

    @Override
    public @NotNull DuctNodePos getDuctNodePosition() {
        ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");
        if (level != null) {
            dimension = level.dimension().location();
        }
        return KelvinExtensions.INSTANCE.toDuctNodePos(getBlockPos(), dimension);
    }

    // --- persistence -----------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        byte mask = 0;
        for (Direction direction : authoredEdges) {
            mask |= (byte) (1 << direction.ordinal());
        }
        tag.putByte("AuthoredEdges", mask);

        if (level != null) {
            saveData(tag, getDuctNodePosition(), level.isClientSide());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        authoredEdges.clear();
        byte mask = tag.getByte("AuthoredEdges");
        for (Direction direction : Direction.values()) {
            if ((mask & (1 << direction.ordinal())) != 0) {
                authoredEdges.add(direction);
            }
        }

        // Held rather than applied: there is no node to load into yet, and creating one later
        // would reset it. The deferred pass in restoreNodeIfMissing() applies it in the right order.
        // The client keeps its own synced copy of node state, so it has nothing to restore here.
        if (level == null || !level.isClientSide()) {
            pendingNodeData = tag.copy();
        }
    }
}
