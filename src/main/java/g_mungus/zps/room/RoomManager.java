package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.ModGasTags;
import g_mungus.zps.networking.RoomBreachS2CPacket;
import g_mungus.zps.world.AtmosphericComposition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;
import org.valkyrienskies.kelvin.util.GasPhysics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every sealed room in a level.
 *
 * <p>Rooms are only looked for where something tries to put gas — a vent or a leaking pipe — and a
 * room only exists once gas has gone into it. From then on it replaces the outside atmosphere for
 * anything whose eyes are inside. A room is only checked again when a block on its boundary or in
 * its interior changes shape, once per tick however many changed; if air can then get out, the room
 * is gone and so is its gas.
 *
 * <p>No wall is perfect: every tick a room leaks back toward the outside atmosphere, faster the more
 * wall it has for its size. Once it has caught up, and what is fed in would not hold it off the
 * outside either, it goes dormant — no gas of its own, replacing nothing — so a trickle into a big
 * room is lost in it while a real supply builds up. A dormant room keeps its walls, so gas going in again wakes it without another
 * search, and is forgotten if a wall changes or nothing comes in for a while.
 *
 * <p>Flames ({@link Burner}) are seen to every {@link #BURN_INTERVAL} ticks: each needs the gas
 * around it breathable as an entity finds it, and goes out where it is not. They leave the gas as
 * it is, and make no room.
 */
public final class RoomManager extends SavedData {

    private static final String KEY = "zps_rooms";
    private static final Factory<RoomManager> FACTORY = new Factory<>(RoomManager::new, RoomManager::load, null);

    /** How long a position that failed to seal is left alone before gas there looks again, in ticks. */
    static final int FAILED_SCAN_COOLDOWN = 100;
    /** How long a dormant room is remembered with nothing going in, in ticks. */
    public static final int DORMANT_TICKS = 1200;
    /** From this pressure up, a room lets out some of what is put into it. */
    public static final double RELEASE_START_PA = 1_000_000.0;
    /** From this pressure up, a room lets out as much gas as is put into it. */
    public static final double MAX_PRESSURE_PA = 10_000_000.0;
    /** How often flames are seen to, in ticks. */
    static final int BURN_INTERVAL = 20;
    /** Most breach puffs drawn for one room coming open. */
    private static final int MAX_BREACH_PUFFS = 4;

    private final Int2ObjectMap<Room> rooms = new Int2ObjectOpenHashMap<>();
    private final Long2IntMap byInterior = new Long2IntOpenHashMap();
    private final Long2ObjectMap<IntList> byBoundary = new Long2ObjectOpenHashMap<>();
    private int nextId = 1;

    /**
     * Positions a scan found no room around, and the game time they may be scanned again: where the
     * scan started, and every cell it reached, which leads out the same way.
     */
    private final Long2LongMap failedUntil = new Long2LongOpenHashMap();
    /** Rooms with a changed block since the last tick, and which positions changed. */
    private final Int2ObjectMap<LongSet> dirty = new Int2ObjectOpenHashMap<>();
    /** Rooms among those where something gained material, which might have cut them in two. */
    private final IntSet needsFullCheck = new IntOpenHashSet();
    private final Flames flames = new Flames();

    public RoomManager() {
        byInterior.defaultReturnValue(-1);
    }

    public static RoomManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    // --- lookup ------------------------------------------------------------------------------

    /** The active room {@code pos} is inside, or null. Dormant rooms replace nothing, so they are left out. */
    public @Nullable Room roomAt(BlockPos pos) {
        Room room = knownRoomAt(pos);
        return room != null && room.isActive() ? room : null;
    }

    /**
     * The active room around {@code entity}'s eyes. A room on a moving grid comes first — the eyes
     * of something on a sublevel or a ship are looked up in the grid's own blocks, for each grid
     * the entity touches — then a room in the world. An entity kept in a grid's own space, a rider
     * on a seat, already has its eyes there.
     */
    public @Nullable Room roomAround(Entity entity) {
        if (rooms.isEmpty()) {
            return null;
        }
        Vec3 eyes = entity.getEyePosition();
        BlockPos eyeCell = BlockPos.containing(eyes);
        if (Compat.gridOf(entity.level(), eyeCell) == null) {
            for (GridSpace grid : Compat.gridsTouching(entity.level(), entity.getBoundingBox())) {
                Room room = roomAt(BlockPos.containing(grid.toLocal(eyes)));
                if (room != null) {
                    return room;
                }
            }
        }
        return roomAt(eyeCell);
    }

    /** The room {@code pos} is inside, active or dormant, or null. */
    public @Nullable Room knownRoomAt(BlockPos pos) {
        int id = byInterior.get(pos.asLong());
        return id < 0 ? null : rooms.get(id);
    }

    public int roomCount() {
        return rooms.size();
    }

    /** The pressure and temperature of the gas around something, as a gauge reads them. */
    public record Ambient(double pressurePa, double temperatureK) {
    }

    /**
     * The gas at {@code pos}: the room it is in, if any, otherwise the outside atmosphere. Pressure
     * is worked out the way Kelvin works out a node's, from the gas's mass over its volume — the
     * room's, or one cubic metre of the outside air. Nothing there to carry it reads as zero.
     */
    public Ambient ambientAt(ServerLevel level, BlockPos pos) {
        Map<GasType, Double> kg = new HashMap<>();
        double volume;
        double temperatureK;
        Room room = roomAt(pos);
        if (room != null) {
            return new Ambient(pressureOf(room), Double.isNaN(room.temperatureK()) ? 0 : room.temperatureK());
        } else {
            outsideAir(level, pos, 1).forEach((gas, mass) -> addByType(kg, gas, mass));
            volume = 1;
            temperatureK = AtmosphereExposure.temperatureK(level, pos);
        }
        if (Double.isNaN(temperatureK)) {
            return new Ambient(0, 0);
        }
        return new Ambient(GasPhysics.INSTANCE.calcPressureFromGamma(kg, volume, temperatureK), temperatureK);
    }

    /**
     * How strongly gas let out at {@code into} floats or sinks in what it enters: from 1, rising as
     * hard as it can, through 0, neither, to -1, falling freely. NaN when there is no gas to judge.
     *
     * <p>The gas spreads to the pressure around it, so at the same pressure the two densities stand
     * as the inverse of each side's gas constant times its temperature: a light gas or a hot one
     * rises, a heavy or a cold one sinks. Into vacuum there is nothing to hold anything up.
     *
     * @param gas          the gas let out, by mass or by any weight in proportion
     * @param temperatureK its temperature
     */
    public double buoyancy(ServerLevel level, BlockPos into, Map<ResourceLocation, Double> gas, double temperatureK) {
        Map<GasType, Double> escaping = new HashMap<>();
        gas.forEach((id, mass) -> addByType(escaping, id, mass));
        double gasRT = GasPhysics.INSTANCE.mixtureR(escaping) * temperatureK;
        if (!(gasRT > 0)) {
            return Double.NaN;
        }

        Map<GasType, Double> around = new HashMap<>();
        double aroundK;
        Room room = roomAt(into);
        if (room != null) {
            room.massKg().forEach((id, mass) -> addByType(around, id, mass));
            aroundK = room.temperatureK();
        } else {
            outsideAir(level, into, 1).forEach((id, mass) -> addByType(around, id, mass));
            aroundK = AtmosphereExposure.temperatureK(level, into);
        }
        double aroundRT = GasPhysics.INSTANCE.mixtureR(around) * aroundK;
        if (!(aroundRT > 0)) {
            return -1;
        }
        // Density of the gas over density of what is around it.
        double ratio = aroundRT / gasRT;
        return (1 - ratio) / Math.max(1, ratio);
    }

    /** A room's pressure, in pascals, worked out as Kelvin works out a node's. */
    public static double pressureOf(Room room) {
        if (Double.isNaN(room.temperatureK())) {
            return 0;
        }
        Map<GasType, Double> kg = new HashMap<>();
        room.massKg().forEach((gas, mass) -> addByType(kg, gas, mass));
        return GasPhysics.INSTANCE.calcPressureFromGamma(kg, room.volume(), room.temperatureK());
    }

    private static void addByType(Map<GasType, Double> kg, ResourceLocation gas, double mass) {
        GasType type = GasTypeRegistry.INSTANCE.getGasType(gas);
        if (type != null && mass > 0) {
            kg.merge(type, mass, Double::sum);
        }
    }

    // --- gas in ------------------------------------------------------------------------------

    /**
     * Gas let out into the world at {@code at}. If {@code at} is in a room, or seals into one now,
     * the room takes it; otherwise it is gone, as it always was. A room under pressure gives some of
     * its mix back as gas goes in — see {@link #releasedShare} — so it cannot be pumped up without
     * limit, though what is fed in still works its way through.
     */
    public void addGas(ServerLevel level, BlockPos at, Map<GasType, Double> kg, double temperatureK) {
        Map<ResourceLocation, Double> byId = new HashMap<>();
        kg.forEach((gas, mass) -> {
            if (mass > 0) {
                byId.merge(gas.getResourceLocation(), mass, Double::sum);
            }
        });
        if (byId.isEmpty()) {
            return;
        }

        Room room = roomFor(level, at);
        if (room == null) {
            return;
        }
        double released = releasedShare(pressureOf(room));
        room.feed(byId, temperatureK);
        if (released > 0) {
            room.release(released * byId.values().stream().mapToDouble(Double::doubleValue).sum());
        }
        setDirty();
    }

    /**
     * The share of gas going in that a room at {@code pressurePa} lets back out: none up to
     * {@link #RELEASE_START_PA}, rising linearly to all of it at {@link #MAX_PRESSURE_PA}, where
     * the room's mass can rise no further.
     */
    public static double releasedShare(double pressurePa) {
        return Mth.clamp((pressurePa - RELEASE_START_PA) / (MAX_PRESSURE_PA - RELEASE_START_PA), 0.0, 1.0);
    }

    /**
     * The room gas going in or out at {@code at} belongs to: the one it is in, woken if dormant, or
     * one sealed around it now, filled with the outside air. Null if air can get out.
     */
    private @Nullable Room roomFor(ServerLevel level, BlockPos at) {
        Room room = knownRoomAt(at);
        if (room == null) {
            return seal(level, at);
        }
        if (!room.isActive()) {
            room.wake(outsideAir(level, room.anchor(), room.volume()), AtmosphereExposure.temperatureK(level, room.anchor()));
            setDirty();
        }
        return room;
    }

    /** Look for a room around {@code at} and register it, filled with the outside air. */
    private @Nullable Room seal(ServerLevel level, BlockPos at) {
        long key = at.asLong();
        long now = level.getGameTime();
        if (failedUntil.containsKey(key)) {
            if (failedUntil.get(key) > now) {
                return null;
            }
            failedUntil.remove(key);
        }

        LongSet escaped = new LongOpenHashSet();
        RoomScan scan = RoomScanner.scan(at, RoomScanner.shapes(level), ZPSConfig.roomMaxVolume(), escaped);
        if (scan == null) {
            if (failedUntil.size() > 4096) {
                failedUntil.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
            }
            // Everything else in the same open space would only find the same way out.
            long until = now + FAILED_SCAN_COOLDOWN;
            failedUntil.put(key, until);
            escaped.forEach(cell -> failedUntil.put(cell, until));
            return null;
        }

        Room room = new Room(nextId++, scan, at, AtmosphereExposure.temperatureK(level, at));
        room.add(outsideAir(level, at, scan.volume()), room.temperatureK());
        rooms.put(room.id(), room);
        index(room);
        setDirty();
        ZPSMod.LOGGER.debug("Room {} sealed at {}: {} m^3", room.id(), at, room.volume());
        return room;
    }

    /**
     * What a room of {@code volume} cells around {@code at} held of the outside atmosphere when it
     * sealed, in kilograms: each gas's share at its own density, thinned with height. Vacuum and
     * gases no mod registers weigh nothing. A room on a moving grid is judged by the air where the
     * grid is in the world ({@link AtmosphereExposure#inWorld}), not at its plot.
     */
    static Map<ResourceLocation, Double> outsideAir(ServerLevel level, BlockPos at, int volume) {
        at = AtmosphereExposure.inWorld(level, at);
        Biome biome = level.getBiome(at).value();
        double thinning = AtmosphericComposition.getDensity(level, at);
        Map<ResourceLocation, Double> kg = new HashMap<>();
        AtmosphericComposition.of(biome).at(at.getY()).forEach((gas, fraction) -> {
            GasType type = GasTypeRegistry.INSTANCE.getGasType(gas);
            if (type != null) {
                kg.put(gas, fraction * type.getDensity() * thinning * volume);
            }
        });
        return kg;
    }

    // --- gas out -----------------------------------------------------------------------------

    /** Gas drawn in from the world, in kilograms per gas, all at one temperature. */
    public record Draw(Map<GasType, Double> kg, double temperatureK) {
        public static final Draw NOTHING = new Draw(Map.of(), Double.NaN);

        public boolean isEmpty() {
            return kg.isEmpty();
        }
    }

    /**
     * What {@code volumeM3} of the gas at {@code at} holds, without taking it: see
     * {@link #drawGas}. Lets a machine check it has somewhere to put the gas before it takes any.
     */
    public Draw peekGas(ServerLevel level, BlockPos at, double volumeM3) {
        return intake(level, at, volumeM3, false);
    }

    /**
     * Take {@code volumeM3} of the gas at {@code at} out of the world. In a room, or a space that
     * seals into one now, that much of the room's own mix comes out of it, so the room thins as it
     * is drawn on — never more than the room holds. Outdoors it is that much of the atmosphere
     * there, which never runs short. Nothing at all in vacuum.
     */
    public Draw drawGas(ServerLevel level, BlockPos at, double volumeM3) {
        return intake(level, at, volumeM3, true);
    }

    private Draw intake(ServerLevel level, BlockPos at, double volumeM3, boolean take) {
        if (!(volumeM3 > 0)) {
            return Draw.NOTHING;
        }
        Room room = roomFor(level, at);

        Map<ResourceLocation, Double> byId = new HashMap<>();
        double temperatureK;
        if (room == null) {
            outsideAir(level, at, 1).forEach((gas, mass) -> byId.put(gas, mass * volumeM3));
            temperatureK = AtmosphereExposure.temperatureK(level, at);
        } else {
            double share = Math.min(1.0, volumeM3 / Math.max(1, room.volume()));
            room.massKg().forEach((gas, mass) -> byId.put(gas, mass * share));
            temperatureK = room.temperatureK();
            if (take) {
                Map<ResourceLocation, Double> taken = room.draw(byId);
                byId.clear();
                byId.putAll(taken);
                setDirty();
            }
        }
        if (Double.isNaN(temperatureK)) {
            return Draw.NOTHING;
        }
        Map<GasType, Double> kg = new HashMap<>();
        byId.forEach((gas, mass) -> addByType(kg, gas, mass));
        return kg.isEmpty() ? Draw.NOTHING : new Draw(kg, temperatureK);
    }

    // --- changes -----------------------------------------------------------------------------

    /**
     * A block changed. If it is part of a room and its shape changed, the room is checked again at
     * the end of the tick.
     */
    public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        long key = pos.asLong();
        int interior = byInterior.get(key);
        IntList bounding = byBoundary.get(key);
        if (Burner.of(oldState) != null || Burner.of(newState) != null) {
            flames.onBlockChanged(pos, newState);
        }
        if (interior < 0 && bounding == null) {
            return;
        }
        VoxelShape before = RoomScanner.shapeOf(level, pos, oldState);
        VoxelShape after = RoomScanner.shapeOf(level, pos, newState);
        if (before == after || !Shapes.joinIsNotEmpty(before, after, BooleanOp.NOT_SAME)) {
            return;
        }
        // Only taking material away can never part a room, which lets it grow without a full flood.
        boolean opened = !Shapes.joinIsNotEmpty(after, before, BooleanOp.ONLY_FIRST);
        if (interior >= 0) {
            markDirty(interior, key, opened);
        }
        if (bounding != null) {
            for (int i = 0; i < bounding.size(); i++) {
                markDirty(bounding.getInt(i), key, opened);
            }
        }
    }

    private void markDirty(int id, long pos, boolean opened) {
        dirty.computeIfAbsent(id, ignored -> new LongOpenHashSet()).add(pos);
        if (!opened) {
            needsFullCheck.add(id);
        }
    }

    /** Check every room that had a block change this tick, then let every loaded room leak, and see to the flames. */
    public void tick(ServerLevel level) {
        recheckChanged(level);
        leak(level);
        if (level.getGameTime() % BURN_INTERVAL == 0) {
            burnFlames(level);
        }
    }

    /** A chunk loaded: its flames are tracked. */
    public void onChunkLoaded(LevelChunk chunk) {
        flames.onChunkLoaded(chunk);
    }

    public void onChunkUnloaded(ChunkPos chunk) {
        flames.onChunkUnloaded(chunk);
    }

    /**
     * See to every loaded flame, as {@link #tick} does every {@link #BURN_INTERVAL} ticks: put out
     * any without what it needs. Public so a test can run it on cue.
     */
    public void burnFlames(ServerLevel level) {
        flames.refresh(level);
        for (long cell : flames.cells()) {
            BlockPos pos = BlockPos.of(cell);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            if (Burner.of(level.getBlockState(pos)) != null && !breathableAt(level, pos)) {
                Flames.extinguish(level, pos);
            }
        }
    }

    /**
     * Whether the gas at {@code pos} is breathable, as an entity would find it there: the active room
     * it is in, otherwise the outside air where it is in the world.
     */
    private boolean breathableAt(ServerLevel level, BlockPos pos) {
        Room room = roomAt(pos);
        if (room != null) {
            return ModGasTags.isBreathable(level, room.gases());
        }
        return outsideIsBreathable(level, pos);
    }

    /** Whether the outside air at {@code pos}, where it is in the world, is breathable as an entity finds it. */
    private static boolean outsideIsBreathable(ServerLevel level, BlockPos pos) {
        BlockPos air = AtmosphereExposure.inWorld(level, pos);
        return AtmosphereExposure.isBreathable(level, AtmosphericComposition.of(level.getBiome(air).value()), air.getY());
    }

    private void leak(ServerLevel level) {
        if (rooms.isEmpty()) {
            return;
        }
        double rate = ZPSConfig.roomLeakRate();
        long now = level.getGameTime();
        List<Room> forgotten = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (!room.isActive()) {
                if (now - room.dormantSince() > DORMANT_TICKS) {
                    forgotten.add(room);
                }
                continue;
            }
            BlockPos anchor = room.anchor();
            if (!level.hasChunkAt(anchor)) {
                // Unloaded rooms keep what they held until someone is back.
                continue;
            }
            Map<ResourceLocation, Double> outside = outsideAir(level, anchor, room.volume());
            double outsideK = AtmosphereExposure.temperatureK(level, anchor);
            double fraction = leakFraction(room, rate);
            room.leak(outside, outsideK, fraction);
            if (room.hasSettled(outside, outsideK, fraction)) {
                room.sleep(now);
            }
        }
        forgotten.forEach(this::dissolve);
        setDirty();
    }

    /**
     * The share of the way back to the outside a room goes in one tick: {@code rate} per block of
     * wall, spread over the air inside. A small room has more wall for its air, so it leaks faster.
     */
    public static double leakFraction(Room room, double rate) {
        return Math.min(1.0, rate * room.surfaceArea() / Math.max(1, room.volume()));
    }

    private void recheckChanged(ServerLevel level) {
        if (dirty.isEmpty()) {
            return;
        }
        Int2ObjectMap<LongSet> changed = new Int2ObjectOpenHashMap<>(dirty);
        IntSet full = new IntOpenHashSet(needsFullCheck);
        dirty.clear();
        needsFullCheck.clear();
        for (Int2ObjectMap.Entry<LongSet> entry : changed.int2ObjectEntrySet()) {
            Room room = rooms.get(entry.getIntKey());
            if (room == null) {
                continue;
            }
            if (room.isActive()) {
                if (full.contains(room.id()) || !grow(level, room, entry.getValue())) {
                    recheck(level, room, entry.getValue());
                }
            } else {
                // Nothing in it to lose; the next gas in will look for walls afresh.
                dissolve(room);
            }
        }
    }

    /**
     * Settle a room whose changed cells have only opened up, from those cells alone — see
     * {@link RoomScanner#grow}. Mining out a cave room costs the new space, not the cave.
     *
     * @return false if it could not be settled that way, and the room needs flooding afresh
     */
    private boolean grow(ServerLevel level, Room room, LongSet changed) {
        LongSet added = new LongOpenHashSet();
        LongSet newBoundary = new LongOpenHashSet();
        Long2LongMap pocketsAfter = new Long2LongOpenHashMap();
        RoomScanner.Growth growth = RoomScanner.grow(room.interior(), room.pockets(), changed,
                RoomScanner.shapes(level), ZPSConfig.roomMaxVolume(), added, newBoundary, pocketsAfter);
        switch (growth) {
            case UNSURE -> {
                return false;
            }
            case ESCAPED -> breach(level, room, changed);
            case GREW -> {
                if (added.isEmpty() && newBoundary.isEmpty() && pocketsAfter.isEmpty()) {
                    return true;
                }
                List<Room> merged = new ArrayList<>();
                for (LongIterator it = added.iterator(); it.hasNext(); ) {
                    long cell = it.nextLong();
                    int other = byInterior.put(cell, room.id());
                    if (other >= 0 && other != room.id() && rooms.get(other) != null && !merged.contains(rooms.get(other))) {
                        // A wall between two rooms came down: they are one now.
                        merged.add(rooms.get(other));
                    }
                    removeBoundary(cell, room.id());
                }
                for (LongIterator it = newBoundary.iterator(); it.hasNext(); ) {
                    IntList ids = byBoundary.computeIfAbsent(it.nextLong(), ignored -> new IntArrayList(2));
                    if (!ids.contains(room.id())) {
                        ids.add(room.id());
                    }
                }
                room.grow(added, newBoundary, pocketsAfter);
                for (Room other : merged) {
                    room.absorb(other);
                    dissolve(other);
                }
                setDirty();
            }
        }
        return true;
    }

    private void removeBoundary(long cell, int id) {
        IntList ids = byBoundary.get(cell);
        if (ids != null) {
            ids.rem(id);
            if (ids.isEmpty()) {
                byBoundary.remove(cell);
            }
        }
    }

    /** Flood a changed room afresh, to see whether it is still sealed and what shape it is now. */
    private void recheck(ServerLevel level, Room room, LongSet changed) {
        // Air reaching unloaded ground past the old walls has got out; reaching part of the room
        // itself that is not loaded only means there is no telling.
        boolean[] hitUnloadedInterior = {false};
        Function<BlockPos, VoxelShape> world = RoomScanner.shapes(level);
        Function<BlockPos, VoxelShape> shapes = pos -> {
            VoxelShape shape = world.apply(pos);
            if (shape == null && !level.isOutsideBuildHeight(pos) && room.interior().contains(pos.asLong())) {
                hitUnloadedInterior[0] = true;
            }
            return shape;
        };

        BlockPos start = openCell(room, changed, shapes);
        if (start == null) {
            // Filled in solid, every cell of it.
            dissolve(room);
            return;
        }
        RoomScan scan = RoomScanner.scan(start, shapes, ZPSConfig.roomMaxVolume());
        if (scan == null) {
            if (hitUnloadedInterior[0]) {
                // Part of it is not loaded, so whether it leaks cannot be told. Leave it be.
                return;
            }
            breach(level, room, changed);
            return;
        }

        unindex(room);
        room.reshape(scan);
        // A wall between two rooms came down: they are one now.
        for (LongIterator it = scan.interior().iterator(); it.hasNext(); ) {
            int other = byInterior.get(it.nextLong());
            if (other >= 0 && other != room.id()) {
                Room merged = rooms.get(other);
                if (merged != null) {
                    room.absorb(merged);
                    dissolve(merged);
                }
            }
        }
        index(room);
        setDirty();
    }

    /**
     * An interior cell air can still be in, to fill from. A cell wholly in the room and unchanged
     * if there is one: a fill starts in every pocket of its cell, so starting in a closed door's
     * would take in the outside pocket too and find the room open.
     */
    private static @Nullable BlockPos openCell(Room room, LongSet changed, Function<BlockPos, VoxelShape> shapes) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos fallback = null;
        for (LongIterator it = room.interior().iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            cursor.set(cell);
            VoxelShape shape = shapes.apply(cursor);
            if (shape != null && RoomScanner.holdsAir(shape)) {
                if (!room.pockets().containsKey(cell) && !changed.contains(cell)) {
                    return cursor.immutable();
                }
                if (fallback == null) {
                    fallback = cursor.immutable();
                }
            }
        }
        return fallback;
    }

    /** Air got out: draw it going and forget the room. */
    private void breach(ServerLevel level, Room room, LongSet changed) {
        if (room.totalMassKg() > 0) {
            List<BlockPos> holes = new ArrayList<>();
            List<Direction> outwards = new ArrayList<>();
            for (LongIterator it = changed.iterator(); it.hasNext() && holes.size() < MAX_BREACH_PUFFS; ) {
                BlockPos pos = BlockPos.of(it.nextLong());
                Direction outward = outwardFrom(level, room, pos);
                if (outward != null && RoomScanner.holdsAir(RoomScanner.shapeOf(level, pos, level.getBlockState(pos)))) {
                    holes.add(pos);
                    outwards.add(outward);
                }
            }
            float perPuff = holes.isEmpty() ? 0 : (float) (room.totalMassKg() / holes.size());
            for (int i = 0; i < holes.size(); i++) {
                float buoyancy = (float) buoyancy(level, holes.get(i).relative(outwards.get(i)), room.massKg(), room.temperatureK());
                PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(holes.get(i)),
                        new RoomBreachS2CPacket(holes.get(i), outwards.get(i), perPuff, buoyancy));
            }
        }
        ZPSMod.LOGGER.debug("Room {} breached", room.id());
        dissolve(room);
    }

    /**
     * The way out of the room through {@code pos}: away from the interior cell beside it, toward
     * open air that is not. A hole can be in the room itself — an opened door's cells were partly
     * inside — and then the cells beside it within the hole are no way out.
     */
    private static @Nullable Direction outwardFrom(ServerLevel level, Room room, BlockPos pos) {
        Direction fallback = null;
        for (Direction direction : Direction.values()) {
            if (room.interior().contains(pos.relative(direction).asLong())) {
                BlockPos out = pos.relative(direction.getOpposite());
                if (!room.interior().contains(out.asLong())
                        && RoomScanner.holdsAir(RoomScanner.shapeOf(level, out, level.getBlockState(out)))) {
                    return direction.getOpposite();
                }
                if (fallback == null) {
                    fallback = direction.getOpposite();
                }
            }
        }
        return fallback;
    }

    /** Forget a room and everything in it. The blocks are untouched. */
    public void dissolve(Room room) {
        if (rooms.remove(room.id()) == null) {
            return;
        }
        unindex(room);
        dirty.remove(room.id());
        needsFullCheck.remove(room.id());
        setDirty();
    }

    private void index(Room room) {
        for (LongIterator it = room.interior().iterator(); it.hasNext(); ) {
            byInterior.put(it.nextLong(), room.id());
        }
        for (LongIterator it = room.boundary().iterator(); it.hasNext(); ) {
            byBoundary.computeIfAbsent(it.nextLong(), ignored -> new IntArrayList(2)).add(room.id());
        }
    }

    private void unindex(Room room) {
        for (LongIterator it = room.interior().iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (byInterior.get(cell) == room.id()) {
                byInterior.remove(cell);
            }
        }
        for (LongIterator it = room.boundary().iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            IntList ids = byBoundary.get(cell);
            if (ids != null) {
                ids.rem(room.id());
                if (ids.isEmpty()) {
                    byBoundary.remove(cell);
                }
            }
        }
    }

    // --- persistence -------------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (Room room : rooms.values()) {
            // Dormant rooms are only a shortcut, and cheap to find again.
            if (room.isActive()) {
                list.add(room.toNbt());
            }
        }
        tag.put("Rooms", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    /** Public so a test can round-trip the data; the level itself always goes through {@link #get}. */
    public static RoomManager load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomManager manager = new RoomManager();
        manager.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag entry : tag.getList("Rooms", Tag.TAG_COMPOUND)) {
            Room room = Room.fromNbt((CompoundTag) entry);
            manager.rooms.put(room.id(), room);
            manager.index(room);
        }
        return manager;
    }
}
