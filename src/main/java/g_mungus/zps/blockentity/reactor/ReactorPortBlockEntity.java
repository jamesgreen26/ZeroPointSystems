package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.gas.core.FilteredOneWayCompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.reactor.ReactorPortBlock;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.Map;

/**
 * The working half of a {@link ReactorPortBlock}, doing whichever job its mode asks for.
 *
 * <p>Everything mode-specific happens between the stub and the chamber node; the outer face is an
 * ordinary duct joint and never knows which mode the port is in. Both modes hold back the same
 * gases, a {@link GasFilter} chosen in the screen alongside the mode: input holds it on the
 * chamber edge, output applies it to what the pump picks up.
 *
 * <p><b>Input:</b> keeps a one-way, filtered edge from the stub into the chamber in place,
 * throttled by redstone. The edge cannot be negotiated face-to-face like every other gas edge —
 * the chamber node is somewhere inside the cavity, not next door — so it is authored here, rebuilt
 * when the redstone level or the filter moves, taken down when the mode changes, and torn down by
 * the reactor when it dissolves.
 *
 * <p><b>Output:</b> pumps gas out of the chamber. Each tick it moves up to a fixed mass of
 * whatever passes the filter from the chamber into its own stub, cooled to a temperature a duct
 * can carry, and stops once the stub backs up. Redstone scales that rate down, to nothing at full
 * power. There is no edge at all in this mode, so nothing can drift back into the chamber. The
 * heat it strips off is simply lost: the Heat Exchangers are the only things that turn chamber
 * heat into anything useful.
 *
 * <p>Both modes share the redstone reading: the strongest signal reaching the block. The block
 * pushes a fresh reading on every neighbour change; a block that has just loaded reads it on its
 * first tick instead, since asking neighbours for their signal while the chunk is still loading
 * can pull further chunks in. The level is persisted and mirrored to clients, where the face
 * overlay is tinted by it.
 */
public class ReactorPortBlockEntity extends GasNodeBlockEntity {

    public static final int MAX_REDSTONE_LEVEL = 15;

    private static final String REDSTONE_TAG = "Redstone";
    private static final String FILTER_TAG = "Filter";

    /** A duct's bore over one block: the stub's own half plus the wall's. */
    public static final double CHAMBER_EDGE_RADIUS = 0.125;
    private static final double CHAMBER_EDGE_LENGTH = 0.5;
    private static final double MIN_TRANSFER = 1e-9;

    private int redstoneLevel;
    private boolean redstoneStale = true;

    private GasFilter filter = GasFilter.PASS_ALL;

    /** Gas drawn per tick in output mode, averaged over a sync window, so clients see a rate. */
    private double drawnSinceSync;
    private double totalDrawn;

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_PORT.get(), pos, state);
    }

    public ReactorPortMode getMode() {
        return ReactorPortBlock.mode(getBlockState());
    }

    /** Which gases the port lets through, in either mode. Valid on both sides. */
    public GasFilter getFilter() {
        return filter;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (redstoneStale) {
            refreshRedstoneLevel();
        }
        switch (getMode()) {
            case INPUT -> ensureChamberEdge(serverLevel);
            case OUTPUT -> draw(serverLevel);
        }
        syncNodeState();
    }

    /** The reactor this port serves, or null if it is not facing into one. */
    public Reactor reactor(ServerLevel serverLevel) {
        return ReactorManager.get(serverLevel).reactorServedBy(worldPosition, ReactorPortBlock.facing(getBlockState()));
    }

    // --- settings ---------------------------------------------------------------------------

    /** What the screen sends: the direction and the filter together. Server only. */
    public void setSettings(ReactorPortMode mode, GasFilter newFilter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (newFilter.blocked().size() > GasFilter.MAX_GASES) {
            return;
        }
        if (!newFilter.equals(filter)) {
            // The input edge is rebuilt on the next tick once it no longer matches; the pump
            // reads the filter live.
            filter = newFilter;
            setChanged();
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        setMode(mode);
    }

    /**
     * Switch the port over. Server only. The old mode's chamber edge goes first; writing the new
     * state then runs the block's placement hook, which renegotiates the outer face. The stub's
     * node and whatever gas it holds stay put.
     */
    public void setMode(ReactorPortMode mode) {
        if (!(level instanceof ServerLevel serverLevel) || getMode() == mode) {
            return;
        }
        if (getMode() == ReactorPortMode.INPUT) {
            removeChamberEdge(serverLevel);
        }
        serverLevel.setBlock(worldPosition, getBlockState().setValue(ReactorPortBlock.MODE, mode), Block.UPDATE_ALL);
        setChanged();
    }

    // --- throttle ---------------------------------------------------------------------------

    /**
     * How far redstone closes the chamber side, as the aperture to hand Kelvin: nothing at zero
     * power, {@code -CHAMBER_EDGE_RADIUS} at full power, which shuts the passage entirely.
     */
    public double chamberAperture() {
        return -CHAMBER_EDGE_RADIUS * redstoneLevel / MAX_REDSTONE_LEVEL;
    }

    /** How much of its full rate the port runs at: one unpowered, zero at full power. */
    private double openness() {
        return 1.0 - (double) redstoneLevel / MAX_REDSTONE_LEVEL;
    }

    // --- input ------------------------------------------------------------------------------

    /** Make sure the chamber edge exists and reflects the throttle and filter, rebuilding it if not. */
    private void ensureChamberEdge(ServerLevel serverLevel) {
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return;
        }
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos own = getDuctNodePosition();
        DuctNodePos host = reactor.hostNodePos(serverLevel);
        if (kelvin.getNodeAt(own) == null || !(kelvin.getNodeAt(host) instanceof ReactorChamberNode)) {
            return;
        }

        FilteredOneWayCompositeDuctEdge desired = chamberEdge(own, host);
        DuctEdge existing = kelvin.getEdgeBetween(own, host);
        if (existing != null && desired.matches(existing)) {
            return;
        }
        kelvin.removeEdge(own, host);
        kelvin.addEdge(desired.getNodeA(), desired.getNodeB(), desired);
    }

    /** The input edge: a filtered check valve from the stub into the chamber, narrowed by redstone. */
    private FilteredOneWayCompositeDuctEdge chamberEdge(DuctNodePos own, DuctNodePos host) {
        GasEdgeNegotiator.EdgeKey key = GasEdgeNegotiator.canonical(own, host);
        double aperture = chamberAperture();
        FilteredOneWayCompositeDuctEdge edge = new FilteredOneWayCompositeDuctEdge(
                aperture < 0 ? ConnectionType.APERTURE_FILTERED_ONEWAY : ConnectionType.FILTERED_ONEWAY,
                key.a(), key.b(), CHAMBER_EDGE_RADIUS, CHAMBER_EDGE_LENGTH);
        // Kelvin: reversed == false lets gas flow from nodeA to nodeB. We want stub -> chamber.
        edge.setReversed(!key.a().equals(own));
        edge.setAperture(aperture);
        edge.setFilter(filter);
        return edge;
    }

    private void removeChamberEdge(ServerLevel serverLevel) {
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return;
        }
        KelvinMod.INSTANCE.forceGetKelvin().removeEdge(getDuctNodePosition(), reactor.hostNodePos(serverLevel));
    }

    // --- output -----------------------------------------------------------------------------

    private void draw(ServerLevel serverLevel) {
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return;
        }
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos own = getDuctNodePosition();
        DuctNodePos host = reactor.hostNodePos(serverLevel);
        if (kelvin.getNodeAt(own) == null || !(kelvin.getNodeAt(host) instanceof ReactorChamberNode)) {
            return;
        }
        // Gated on the stub's own pressure, not the chamber's: a well-run chamber holds a few
        // grams at a few kilopascals, less than the stub, and would never drain otherwise.
        if (getPressure() >= ZPSConfig.exhaustBackpressureLimitPa()) {
            return;
        }

        // Redstone throttles the pump the way it narrows the input's valve.
        double budget = ZPSConfig.exhaustKgPerTick() * openness();
        double outletTemperature = Math.min(kelvin.getTemperatureAt(host), ZPSConfig.exhaustOutletTemperatureK());

        // The pump takes the mixture as it finds it: each passing gas in proportion to its share
        // of what passes, so a trace gas is not starved by an abundant one.
        Map<GasType, Double> chamber = Map.copyOf(kelvin.getGasMassAt(host));
        double passing = 0;
        for (Map.Entry<GasType, Double> entry : chamber.entrySet()) {
            if (filter.passes(entry.getKey())) {
                passing += entry.getValue();
            }
        }
        if (passing > MIN_TRANSFER && budget > MIN_TRANSFER) {
            double fraction = Math.min(1.0, budget / passing);
            for (Map.Entry<GasType, Double> entry : chamber.entrySet()) {
                GasType gas = entry.getKey();
                if (!filter.passes(gas)) {
                    continue;
                }
                double take = entry.getValue() * fraction;
                if (take <= MIN_TRANSFER) {
                    continue;
                }
                if (kelvin.removeGas(host, gas, take)) {
                    kelvin.addGasAtTemperature(own, gas, take, outletTemperature);
                    drawnSinceSync += take;
                    totalDrawn += take;
                }
            }
        }

        // Whatever arrives, the stub never runs hotter than its outlet rating.
        double temperature = getTemperature();
        double limit = ZPSConfig.exhaustOutletTemperatureK();
        if (temperature > limit) {
            kelvin.modHeatEnergy(own, -(temperature - limit) * kelvin.getNodeHeatCapacity(own));
        }
    }

    /** Everything this port has ever pumped out of a chamber as an output, in kilograms. */
    public double getTotalDrawn() {
        return totalDrawn;
    }

    @Override
    protected double massForSync() {
        return getMode() == ReactorPortMode.OUTPUT
                ? drawnSinceSync / syncInterval()
                : super.massForSync();
    }

    @Override
    protected boolean syncNodeState() {
        if (!super.syncNodeState()) {
            return false;
        }
        drawnSinceSync = 0;
        return true;
    }

    // --- redstone ---------------------------------------------------------------------------

    /** The strongest redstone signal reaching this block, 0..15. Valid on both sides. */
    public int getRedstoneLevel() {
        return redstoneLevel;
    }

    /** Re-read the signal from the block's neighbours. Server only; no-op elsewhere. */
    public void refreshRedstoneLevel() {
        if (level == null || level.isClientSide()) {
            return;
        }
        redstoneStale = false;
        setRedstoneLevel(level.getBestNeighborSignal(worldPosition));
    }

    private void setRedstoneLevel(int newLevel) {
        newLevel = Math.max(0, Math.min(MAX_REDSTONE_LEVEL, newLevel));
        if (newLevel == redstoneLevel) {
            return;
        }
        redstoneLevel = newLevel;
        setChanged();
        if (level != null && !level.isClientSide()) {
            // For the overlay tint. The chamber side picks the new level up on the next tick.
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- persistence and sync ---------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(REDSTONE_TAG, redstoneLevel);
        tag.put(FILTER_TAG, filter.save(new CompoundTag()));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        redstoneLevel = tag.getInt(REDSTONE_TAG);
        filter = GasFilter.load(tag.getCompound(FILTER_TAG));
        // Neighbours may have changed while this block was unloaded; confirm on the first tick.
        redstoneStale = true;
    }

    /**
     * The redstone level and the filter go to clients, for the overlay tint and the screen; node
     * state travels on {@code GasNodeSyncS2CPacket}.
     */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(REDSTONE_TAG, redstoneLevel);
        tag.put(FILTER_TAG, filter.save(new CompoundTag()));
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        redstoneLevel = tag.getInt(REDSTONE_TAG);
        filter = GasFilter.load(tag.getCompound(FILTER_TAG));
    }
}
