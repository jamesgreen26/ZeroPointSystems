package g_mungus.zps.reactor;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every reactor in a level. There is no controller block: a reactor exists because a cavity is
 * sealed, and this is where that fact is kept.
 *
 * <p>Owns detection (a wall block changed — what is sealed now?), the chamber node's life in
 * Kelvin, the per-tick state check that decides ignition, stall, and failure, and the save data
 * that carries all of it, chamber contents included, across a reload.
 */
public final class ReactorManager extends SavedData {

    private static final String KEY = "zps_reactors";
    private static final Factory<ReactorManager> FACTORY =
            new Factory<>(ReactorManager::new, ReactorManager::load, null);

    private final Int2ObjectMap<Reactor> reactors = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<IntList> byWall = new Long2ObjectOpenHashMap<>();
    private final Long2IntMap byInterior = new Long2IntOpenHashMap();
    private int nextId = 1;

    /**
     * The level this data belongs to. Save data is not told its level, but chamber contents are
     * read from Kelvin by a position that includes the dimension, so it is remembered on lookup.
     */
    private @Nullable ServerLevel level;

    public ReactorManager() {
        byInterior.defaultReturnValue(-1);
    }

    public static ReactorManager get(ServerLevel level) {
        ReactorManager manager = level.getDataStorage().computeIfAbsent(FACTORY, KEY);
        manager.level = level;
        return manager;
    }

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    // --- lookup ------------------------------------------------------------------------------

    public Collection<Reactor> all() {
        return reactors.values();
    }

    public @Nullable Reactor byId(int id) {
        return reactors.get(id);
    }

    /** Every reactor this position is part of, as wall or interior. */
    public List<Reactor> reactorsAt(BlockPos pos) {
        List<Reactor> found = new ArrayList<>(2);
        IntList walls = byWall.get(pos.asLong());
        if (walls != null) {
            for (int i = 0; i < walls.size(); i++) {
                Reactor reactor = reactors.get(walls.getInt(i));
                if (reactor != null) {
                    found.add(reactor);
                }
            }
        }
        int interior = byInterior.get(pos.asLong());
        if (interior >= 0) {
            Reactor reactor = reactors.get(interior);
            if (reactor != null && !found.contains(reactor)) {
                found.add(reactor);
            }
        }
        return found;
    }

    public @Nullable Reactor reactorForInterior(BlockPos pos) {
        int id = byInterior.get(pos.asLong());
        return id < 0 ? null : reactors.get(id);
    }

    /**
     * The reactor a functional wall block at {@code pos} serves: the one whose cavity is on its
     * inner face. Null if it is not on any reactor, or is on one but faces the wrong way. A wall
     * shared by two reactors is thereby claimed by whichever one the block faces into.
     */
    public @Nullable Reactor reactorServedBy(BlockPos pos, Direction facing) {
        Reactor reactor = reactorForInterior(pos.relative(facing.getOpposite()));
        return reactor != null && reactor.isWall(pos) ? reactor : null;
    }

    public boolean isTracked(BlockPos pos) {
        long key = pos.asLong();
        return byWall.containsKey(key) || byInterior.containsKey(key);
    }

    // --- detection ---------------------------------------------------------------------------

    /**
     * A reactor wall block was placed at or removed from {@code pos}. Anything it was part of is
     * gone — a hot one erupts — and whatever is sealed around it now is registered.
     */
    public void onWallBlockChanged(ServerLevel level, BlockPos pos, boolean removed) {
        for (Reactor reactor : reactorsAt(pos)) {
            if (removed && reactor.isLit()) {
                ReactorFailures.breach(level, reactor, pos, false);
            } else {
                dissolve(level, reactor);
            }
        }
        rescan(level, pos);
    }

    /**
     * Something changed at a tracked position without going through a wall block's own hooks:
     * a command, an explosion, a block set inside the cavity. Re-check what is there.
     */
    public void onTrackedPositionChanged(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        BlockState state = level.getBlockState(pos);
        boolean wallGone = byWall.containsKey(key) && !state.is(ReactorWallBlock.REACTOR_WALL);
        boolean interiorFilled = byInterior.containsKey(key) && !state.isAir();
        if (!wallGone && !interiorFilled) {
            return;
        }
        for (Reactor reactor : reactorsAt(pos)) {
            if (wallGone && reactor.isLit()) {
                ReactorFailures.breach(level, reactor, pos, false);
            } else {
                dissolve(level, reactor);
            }
        }
        rescan(level, pos);
    }

    private void rescan(ServerLevel level, BlockPos pos) {
        for (CavityScan scan : CavityScanner.scanAround(pos, cells(level), ZPSConfig.reactorMaxInteriorExtent())) {
            register(level, scan);
        }
    }

    private static Function<BlockPos, CavityScanner.Cell> cells(ServerLevel level) {
        return pos -> {
            if (level.isOutsideBuildHeight(pos)) {
                return CavityScanner.Cell.OTHER;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return CavityScanner.Cell.AIR;
            }
            return state.is(ReactorWallBlock.REACTOR_WALL) ? CavityScanner.Cell.WALL : CavityScanner.Cell.OTHER;
        };
    }

    private void register(ServerLevel level, CavityScan scan) {
        Reactor reactor = new Reactor(nextId++, scan);
        reactors.put(reactor.id(), reactor);
        for (long wall : reactor.walls()) {
            byWall.computeIfAbsent(wall, ignored -> new IntArrayList(2)).add(reactor.id());
        }
        for (long cell : reactor.interior()) {
            byInterior.put(cell, reactor.id());
        }
        ensureNode(level, reactor);
        setDirty();
        ZPSMod.LOGGER.debug("Reactor {} sealed at {}: {} m^3, {} walls, compactness {}",
                reactor.id(), reactor.host(), reactor.volume(), reactor.wallCount(), reactor.compactness());
    }

    /** Forget a reactor and take its chamber out of the simulation. The blocks are untouched. */
    public void dissolve(ServerLevel level, Reactor reactor) {
        if (reactors.remove(reactor.id()) == null) {
            return;
        }
        for (long wall : reactor.walls()) {
            IntList ids = byWall.get(wall);
            if (ids != null) {
                ids.rem(reactor.id());
                if (ids.isEmpty()) {
                    byWall.remove(wall);
                }
            }
        }
        for (long cell : reactor.interior()) {
            if (byInterior.get(cell) == reactor.id()) {
                byInterior.remove(cell);
            }
        }

        DuctNetwork<?> kelvin = kelvin();
        DuctNodePos host = reactor.hostNodePos(level);
        // Kelvin's removeNode leaves edges behind; the injectors' internal edges go first.
        for (BlockPos injector : reactor.wallsOf(level, ModBlocks.FUEL_INJECTOR.get())) {
            kelvin.removeEdge(GasEdgeNegotiator.nodePos(level, injector), host);
        }
        kelvin.removeNode(host);
        setDirty();
        ZPSMod.LOGGER.debug("Reactor {} at {} dissolved", reactor.id(), reactor.host());
    }

    // --- chamber node ------------------------------------------------------------------------

    /**
     * Make sure the chamber exists in Kelvin. After a reload it will not, and whatever was saved
     * for it is put back the moment it is recreated.
     */
    public void ensureNode(ServerLevel level, Reactor reactor) {
        DuctNetwork<?> kelvin = kelvin();
        DuctNodePos host = reactor.hostNodePos(level);
        if (kelvin.getNodeAt(host) instanceof ReactorChamberNode) {
            return;
        }
        kelvin.addNode(host, new ReactorChamberNode(host, reactor.id(), reactor.volume(), reactor.wallHeatCapacity()));
        kelvin.markLoaded(host);

        CompoundTag saved = reactor.takeSavedChamber();
        if (saved != null) {
            ReactorChamberNodeIO.restore(kelvin, host, saved);
        }
    }

    // --- tick --------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        if (reactors.isEmpty()) {
            return;
        }
        DuctNetwork<?> kelvin = kelvin();
        double ignition = ZPSConfig.reactorIgnitionTemperatureK();
        double melt = ZPSConfig.reactorMeltTemperatureK();

        for (Reactor reactor : new ArrayList<>(reactors.values())) {
            reactor.rollTickCounters();
            if (!level.isLoaded(reactor.host())) {
                continue;
            }
            ensureNode(level, reactor);
            DuctNodePos host = reactor.hostNodePos(level);

            double temperature = kelvin.getTemperatureAt(host);
            double pressure = kelvin.getPressureAt(host);
            boolean lit = temperature >= ignition;
            reactor.setLit(lit);

            if (lit && !reactor.hasIgnited()) {
                reactor.markIgnited();
                ReactorFailures.award(level, reactor, "reactor_ignited", "ignited");
            }

            if (temperature > melt) {
                ReactorFailures.breach(level, reactor, reactor.randomWall(level.random), true);
            } else if (pressure > reactor.burstPressure()) {
                ReactorFailures.burst(level, reactor, pressure);
            }
        }
        // Chamber contents change every tick, and they are part of what gets saved.
        setDirty();
    }

    public static double aetherFraction(Map<GasType, Double> masses) {
        double total = 0;
        double aether = 0;
        for (Map.Entry<GasType, Double> entry : masses.entrySet()) {
            total += entry.getValue();
            if (entry.getKey() == ModGases.AETHER) {
                aether += entry.getValue();
            }
        }
        return total <= 0 ? 0 : aether / total;
    }

    // --- persistence -------------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        DuctNetwork<?> kelvin = kelvin();
        ListTag list = new ListTag();
        for (Reactor reactor : reactors.values()) {
            if (level != null) {
                DuctNodePos host = reactor.hostNodePos(level);
                if (kelvin.getNodeAt(host) instanceof ReactorChamberNode) {
                    reactor.setSavedChamber(ReactorChamberNodeIO.save(kelvin, host));
                }
            }
            list.add(reactor.toNbt());
        }
        tag.put("Reactors", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    /** Public so a test can round-trip the data; the level itself always goes through {@link #get}. */
    public static ReactorManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ReactorManager manager = new ReactorManager();
        manager.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag entry : tag.getList("Reactors", Tag.TAG_COMPOUND)) {
            Reactor reactor = Reactor.fromNbt((CompoundTag) entry);
            manager.reactors.put(reactor.id(), reactor);
            for (long wall : reactor.walls()) {
                manager.byWall.computeIfAbsent(wall, ignored -> new IntArrayList(2)).add(reactor.id());
            }
            for (long cell : reactor.interior()) {
                manager.byInterior.put(cell, reactor.id());
            }
        }
        return manager;
    }
}
