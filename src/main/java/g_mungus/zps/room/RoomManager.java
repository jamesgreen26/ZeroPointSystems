package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.GasExposure;
import g_mungus.zps.gas.ModGasTags;
import g_mungus.zps.networking.RoomBreachS2CPacket;
import g_mungus.zps.world.AtmosphericComposition;
import g_mungus.zps.world.LocalAtmosphere;
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
import java.util.Comparator;
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
 * is gone and so is its gas. A room parted in two — a door shut across it — becomes a room either
 * side, each with the gas that was on its side, and only the side air can get out of is lost.
 *
 * <p>No wall is perfect: every tick a room leaks back toward the outside atmosphere, faster the more
 * wall it has for its size. The outside is what the room's own cells would hold unsealed, each read
 * where it is ({@link Outside}), so a room lying across biomes leaks toward the mix of them.
 *
 * <p>A room's gas is kept for the room as a whole, but is not one mix throughout: each place has
 * its own outside air, with the room's difference from the outside applied ({@link #airAt}). So
 * sealing a room changes nothing anywhere in it until gas goes in or out, and one that has leaked
 * all the way back is the outside again at every spot. Once it has caught up, and what is fed in would not hold it off the
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
    /** How long a room's reading of the outside atmosphere stands before it is taken again, in ticks. */
    static final int OUTSIDE_INTERVAL = 100;
    /** How often flames are seen to, in ticks. */
    static final int BURN_INTERVAL = 20;
    /** Gas coming together across an opening with at least this much between the pressures either side is drawn crossing. */
    public static final double MERGE_PUFF_PA = 10_000.0;
    /** The side at the lower pressure has to be more than this many cells for the gas going into it to be drawn. */
    public static final int MERGE_PUFF_MIN_CELLS = 8;
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
        BlockPos cell = roomCellAround(entity);
        return cell == null ? null : roomAt(cell);
    }

    /** The gas around {@code entity}'s eyes in the active room they are in ({@link #roomAround}), or null in none. */
    public @Nullable Air airAround(ServerLevel level, Entity entity) {
        BlockPos cell = roomCellAround(entity);
        return cell == null ? null : airAt(level, cell);
    }

    /** The cell of an active room that {@code entity}'s eyes are in, on a grid's own blocks or the world's, or null. */
    private @Nullable BlockPos roomCellAround(Entity entity) {
        if (rooms.isEmpty()) {
            return null;
        }
        Vec3 eyes = entity.getEyePosition();
        BlockPos eyeCell = BlockPos.containing(eyes);
        if (Compat.gridOf(entity.level(), eyeCell) == null) {
            for (GridSpace grid : Compat.gridsTouching(entity.level(), entity.getBoundingBox())) {
                BlockPos cell = BlockPos.containing(grid.toLocal(eyes));
                if (roomAt(cell) != null) {
                    return cell;
                }
            }
        }
        return roomAt(eyeCell) == null ? null : eyeCell;
    }

    /**
     * The gas at one place in a room.
     *
     * @param kgPerM3      what a cubic metre there holds, in kilograms by gas
     * @param temperatureK its temperature, or {@link Double#NaN} for none
     * @param roomVolume   the room's cells, to tell a trace by
     */
    public record Air(Map<ResourceLocation, Double> kgPerM3, double temperatureK, int roomVolume) {

        /**
         * The gas a living thing there breathes, as weights by volume: each gas's mass over its
         * own density, which is how a biome's composition gives its shares
         * ({@link LocalAtmosphere#shares()}), so a room holding the outside air reads as the
         * outside does. Traces do not count — a gas the room would hold less than
         * {@link GasExposure#MIN_GAS_KG} of were it all as it is here — and a place with nothing
         * beyond them is vacuum.
         */
        public Map<ResourceLocation, Double> gases() {
            Map<ResourceLocation, Double> gases = new HashMap<>();
            kgPerM3.forEach((gas, mass) -> {
                if (mass * roomVolume >= GasExposure.MIN_GAS_KG) {
                    gases.put(gas, Room.byVolume(gas, mass));
                }
            });
            if (gases.isEmpty()) {
                gases.put(AtmosphericComposition.VACUUM, 1.0);
            }
            return gases;
        }
    }

    /**
     * The gas at {@code pos} in the active room it is in, or null in none: the outside air there,
     * less the share of each gas the room has lost and plus what it has gained, spread evenly —
     * see {@link Outside#airAt}. In a room whose outside is the same all through, that is the
     * room's one mix.
     */
    public @Nullable Air airAt(ServerLevel level, BlockPos pos) {
        Room room = roomAt(pos);
        return room == null ? null : airIn(level, room, pos);
    }

    /** The gas at {@code pos} as {@code room} has it, whichever room the index says is there just now. */
    private Air airIn(ServerLevel level, Room room, BlockPos pos) {
        Outside outside = outsideOf(level, room);
        return new Air(outside.airAt(room.massKg(), room.volume(), outsideAir(level, pos, 1)),
                outside.temperatureAt(room.temperatureK(), AtmosphereExposure.temperatureK(level, pos)), room.volume());
    }

    /** The pressure of {@code air}, in pascals, worked out as Kelvin works out a node's; nothing to carry it is zero. */
    private static double pressureOf(Air air) {
        if (Double.isNaN(air.temperatureK())) {
            return 0;
        }
        Map<GasType, Double> kg = new HashMap<>();
        air.kgPerM3().forEach((gas, mass) -> addByType(kg, gas, mass));
        return GasPhysics.INSTANCE.calcPressureFromGamma(kg, 1, air.temperatureK());
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
     * The gas at {@code pos}: the room's there, if it is in one, otherwise the outside atmosphere.
     * Pressure is worked out the way Kelvin works out a node's, from the mass of gas in a cubic
     * metre there. Nothing there to carry it reads as zero.
     */
    public Ambient ambientAt(ServerLevel level, BlockPos pos) {
        Map<GasType, Double> kg = new HashMap<>();
        double temperatureK;
        Air air = airAt(level, pos);
        if (air != null) {
            air.kgPerM3().forEach((gas, mass) -> addByType(kg, gas, mass));
            temperatureK = air.temperatureK();
        } else {
            outsideAir(level, pos, 1).forEach((gas, mass) -> addByType(kg, gas, mass));
            temperatureK = AtmosphereExposure.temperatureK(level, pos);
        }
        if (Double.isNaN(temperatureK)) {
            return new Ambient(0, 0);
        }
        return new Ambient(GasPhysics.INSTANCE.calcPressureFromGamma(kg, 1, temperatureK), temperatureK);
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
        Map<ResourceLocation, Double> around;
        double aroundK;
        Air air = airAt(level, into);
        if (air != null) {
            around = air.kgPerM3();
            aroundK = air.temperatureK();
        } else {
            around = outsideAir(level, into, 1);
            aroundK = AtmosphereExposure.temperatureK(level, into);
        }
        return buoyancy(gas, temperatureK, around, aroundK);
    }

    /** As {@link #buoyancy(ServerLevel, BlockPos, Map, double)}, in gas that is {@code around} at {@code aroundK}. */
    private static double buoyancy(Map<ResourceLocation, Double> gas, double temperatureK,
                                   Map<ResourceLocation, Double> around, double aroundK) {
        Map<GasType, Double> escaping = new HashMap<>();
        gas.forEach((id, mass) -> addByType(escaping, id, mass));
        double gasRT = GasPhysics.INSTANCE.mixtureR(escaping) * temperatureK;
        if (!(gasRT > 0)) {
            return Double.NaN;
        }
        Map<GasType, Double> surrounding = new HashMap<>();
        around.forEach((id, mass) -> addByType(surrounding, id, mass));
        double aroundRT = GasPhysics.INSTANCE.mixtureR(surrounding) * aroundK;
        if (!(aroundRT > 0)) {
            return -1;
        }
        // Density of the gas over density of what is around it.
        double ratio = aroundRT / gasRT;
        return (1 - ratio) / Math.max(1, ratio);
    }

    /**
     * A room's pressure overall, in pascals, worked out as Kelvin works out a node's: all its gas
     * over all its volume. What a gauge reads at one place in it is {@link #ambientAt}.
     */
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
            wake(level, room);
            setDirty();
        }
        return room;
    }

    /** Fill a room that holds nothing with the outside air, as a newly sealed room is. */
    private void wake(ServerLevel level, Room room) {
        Outside outside = outsideOf(level, room);
        room.wake(outside.massKg(), outside.temperatureK());
    }

    /**
     * The outside atmosphere of {@code room}, read afresh if its shape has changed or the last
     * reading is {@link #OUTSIDE_INTERVAL} old: the outside moves on — a ship flies somewhere else,
     * a star comes round — but slowly, and a big room has a lot of cells to ask.
     */
    private Outside outsideOf(ServerLevel level, Room room) {
        long now = level.getGameTime();
        Outside outside = room.outsideReadAfter(now - OUTSIDE_INTERVAL);
        if (outside == null) {
            outside = readOutside(level, room.interior());
            room.outsideRead(outside, now);
        }
        return outside;
    }

    private static Outside readOutside(ServerLevel level, LongSet cells) {
        return Outside.of(cells, at -> outsideAir(level, at, 1), at -> AtmosphereExposure.temperatureK(level, at));
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

        List<Room> held = overlapping(scan.interior(), scan.pockets());
        if (!held.isEmpty()) {
            return adopt(level, held, scan);
        }

        Room room = new Room(nextId++, scan, at, Double.NaN);
        wake(level, room);
        rooms.put(room.id(), room);
        index(room);
        setDirty();
        ZPSMod.LOGGER.debug("Room {} sealed at {}: {} m^3", room.id(), at, room.volume());
        return room;
    }

    /**
     * The rooms already holding any of {@code interior}. No two rooms may hold the same air, so
     * space found to seal is theirs rather than a new room's. A cell either side holds only some
     * pockets of is no sign of it: a closed door's cell belongs to the room on each side.
     *
     * @param pockets the cells of {@code interior} only partly held; see {@link RoomScan#pockets()}
     */
    private List<Room> overlapping(LongSet interior, Long2LongMap pockets) {
        List<Room> held = new ArrayList<>();
        for (LongIterator it = interior.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (pockets.containsKey(cell)) {
                continue;
            }
            Room room = rooms.get(byInterior.get(cell));
            if (room != null && !room.pockets().containsKey(cell) && !held.contains(room)) {
                held.add(room);
            }
        }
        return held;
    }

    /**
     * Gas went into a cell no room held, and the space around it turned out to be {@code held}'s
     * already: a wall block of theirs has just gone, and they have not been checked since. The
     * scan is their shape now, so they take it rather than a second room being laid over the same
     * air with another fill of the outside's. More than one of them means the wall between them
     * came down, and they are one now.
     */
    private Room adopt(ServerLevel level, List<Room> held, RoomScan scan) {
        Room room = held.stream().filter(Room::isActive).findFirst().orElse(held.get(0));
        unindex(room);
        room.reshape(scan);
        for (Room other : held) {
            if (other != room) {
                room.absorb(other);
                dissolve(other);
            }
        }
        index(room);
        // The scan has seen every change so far.
        dirty.remove(room.id());
        needsFullCheck.remove(room.id());
        if (!room.isActive()) {
            wake(level, room);
        }
        setDirty();
        return room;
    }

    /**
     * What {@code volume} cells at {@code at} hold of the outside atmosphere, in kilograms: each
     * gas's share at its own density, thinned with height, and run into the next biome's where two
     * meet ({@link LocalAtmosphere}). Vacuum and gases no mod registers weigh nothing. A cell on a
     * moving grid is judged by the air where the grid is in the world
     * ({@link AtmosphereExposure#inWorld}), not at its plot.
     */
    static Map<ResourceLocation, Double> outsideAir(ServerLevel level, BlockPos at, int volume) {
        Map<ResourceLocation, Double> kg = new HashMap<>();
        AtmosphereExposure.outside(level, at).kgPerM3().forEach((gas, mass) -> kg.put(gas, mass * volume));
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
     * seals into one now, that much of the room's gas as it is there ({@link #airAt}) comes out of
     * it, so the room thins as it is drawn on — never more than the room holds. Outdoors it is that much of the atmosphere
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
            Air air = airAt(level, at);
            double drawn = Math.min(volumeM3, room.volume());
            air.kgPerM3().forEach((gas, mass) -> byId.put(gas, mass * drawn));
            temperatureK = air.temperatureK();
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
        Air air = airAt(level, pos);
        if (air != null) {
            return ModGasTags.isBreathable(level, air.gases());
        }
        return outsideIsBreathable(level, pos);
    }

    /** Whether the outside air at {@code pos}, where it is in the world, is breathable as an entity finds it. */
    private static boolean outsideIsBreathable(ServerLevel level, BlockPos pos) {
        return ModGasTags.isBreathable(level, AtmosphereExposure.outside(level, pos).shares());
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
            Outside outside = outsideOf(level, room);
            double fraction = leakFraction(room, rate);
            room.leak(outside.massKg(), outside.temperatureK(), fraction);
            if (room.hasSettled(outside.massKg(), outside.temperatureK(), fraction)) {
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
                // The cells taken in that no room held: sealed space with the outside air in it.
                LongSet space = new LongOpenHashSet();
                for (LongIterator it = added.iterator(); it.hasNext(); ) {
                    long cell = it.nextLong();
                    int other = byInterior.put(cell, room.id());
                    if (other >= 0 && other != room.id() && rooms.get(other) != null) {
                        if (!merged.contains(rooms.get(other))) {
                            // A wall between two rooms came down: they are one now.
                            merged.add(rooms.get(other));
                        }
                    } else if (other < 0) {
                        space.add(cell);
                    }
                    removeBoundary(cell, room.id());
                }
                for (LongIterator it = newBoundary.iterator(); it.hasNext(); ) {
                    IntList ids = byBoundary.computeIfAbsent(it.nextLong(), ignored -> new IntArrayList(2));
                    if (!ids.contains(room.id())) {
                        ids.add(room.id());
                    }
                }
                for (Room other : merged) {
                    send(level, mergePuffs(level, room, other, changed));
                }
                if (!space.isEmpty()) {
                    send(level, growthPuffs(level, room, space, changed));
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

    /** One part of a room that a change has parted: the room it now is, or null if air gets out of it, and its cells. */
    private record Part(@Nullable RoomScan scan, LongSet cells) {
    }

    /**
     * Flood a changed room afresh, to see whether it is still sealed and what shape it is now.
     *
     * <p>A change that adds material can part a room: a door shut across it, a wall built through
     * it. Every part is then flooded on its own. Each one still sealed is a room — the largest
     * stays this one, the rest are new — holding the gas that was on its side
     * ({@link #gasOfParts}); a part air can get out of loses its gas and is no room. Which part a
     * fill happens to start in makes no difference to any of it.
     */
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

        LongSet waiting = openCells(room, changed, shapes);
        if (waiting.isEmpty()) {
            // Filled in solid, every cell of it.
            dissolve(room);
            return;
        }
        List<Part> parts = new ArrayList<>();
        Part largest = null;
        while (!waiting.isEmpty()) {
            long first = waiting.iterator().nextLong();
            BlockPos start = BlockPos.of(first);
            RoomScan scan = RoomScanner.scan(start, shapes, ZPSConfig.roomMaxVolume());
            if (scan == null && hitUnloadedInterior[0]) {
                // Part of it is not loaded, so whether it leaks cannot be told. Leave it be.
                return;
            }
            Part part = new Part(scan, scan != null ? scan.interior() : RoomScanner.reach(start, room.interior(), shapes));
            parts.add(part);
            waiting.removeAll(part.cells());
            waiting.remove(first);
            if (scan != null && (largest == null || scan.volume() > largest.scan().volume())) {
                largest = part;
            }
        }
        if (largest == null) {
            breach(level, room, changed);
            return;
        }

        // With one part and that one sealed the room has only changed shape, and keeps all it held.
        List<Map<ResourceLocation, Double>> gas = parts.size() > 1 ? gasOfParts(level, room, parts) : null;
        double[] kelvin = new double[parts.size()];
        if (gas != null) {
            Outside before = outsideOf(level, room);
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).scan() != null) {
                    kelvin[i] = before.temperatureAt(room.temperatureK(), readOutside(level, parts.get(i).cells()).temperatureK());
                }
            }
            ZPSMod.LOGGER.debug("Room {} parted into {}", room.id(), parts.size());
        }

        for (Part part : parts) {
            if (part.scan() != null) {
                for (Room other : overlapping(part.scan().interior(), part.scan().pockets())) {
                    if (other != room) {
                        send(level, mergePuffs(level, room, other, changed));
                    }
                }
            }
        }
        unindex(room);
        for (int i = 0; i < parts.size(); i++) {
            RoomScan scan = parts.get(i).scan();
            if (scan == null) {
                continue;
            }
            Room part;
            if (parts.get(i) == largest) {
                part = room;
                part.reshape(scan);
            } else {
                part = new Room(nextId++, scan, BlockPos.of(scan.interior().iterator().nextLong()), kelvin[i]);
                rooms.put(part.id(), part);
            }
            if (gas != null) {
                part.setGas(gas.get(i), kelvin[i]);
            }
            // A wall between two rooms came down: they are one now.
            for (Room other : overlapping(scan.interior(), scan.pockets())) {
                if (other != part) {
                    part.absorb(other);
                    dissolve(other);
                }
            }
            index(part);
        }
        setDirty();
    }

    /**
     * What each part of a parted room holds of its gas, in the order of {@code parts}: the gas
     * that was in its cells while the room was whole ({@link Outside#airAt}). A gas the room was
     * short of was short by the same share everywhere, so each part has that share of its own
     * cells' outside air; a gas it had more of had the surplus lying evenly, so each part has its
     * own outside air and the surplus by its volume. All of it is shared out, parts air gets out
     * of included, though what those are given is lost.
     */
    private List<Map<ResourceLocation, Double>> gasOfParts(ServerLevel level, Room room, List<Part> parts) {
        Outside before = outsideOf(level, room);
        List<Map<ResourceLocation, Double>> gas = new ArrayList<>();
        List<Outside> outsides = new ArrayList<>();
        double cells = 0;
        for (Part part : parts) {
            gas.add(new HashMap<>());
            outsides.add(readOutside(level, part.cells()));
            cells += part.cells().size();
        }
        for (Map.Entry<ResourceLocation, Double> held : room.massKg().entrySet()) {
            ResourceLocation id = held.getKey();
            double mass = held.getValue();
            double outside = before.massKg().getOrDefault(id, 0.0);
            double[] weights = new double[parts.size()];
            double total = 0;
            for (int i = 0; i < parts.size(); i++) {
                double own = outsides.get(i).massKg().getOrDefault(id, 0.0);
                double volume = parts.get(i).cells().size();
                weights[i] = mass < outside ? own * mass / outside : own + (mass - outside) * volume / room.volume();
                total += weights[i];
            }
            for (int i = 0; i < parts.size(); i++) {
                // Nothing to go by: share it out by volume.
                double share = total > 0 ? weights[i] / total : parts.get(i).cells().size() / cells;
                gas.get(i).put(id, mass * share);
            }
        }
        return gas;
    }

    /**
     * The interior cells air can still be in, to fill from: the cells wholly in the room and
     * unchanged, as a fill starts in every pocket of its cell, so starting in a closed door's would
     * take in the outside pocket too and find the room open. With none of those, any one cell that
     * holds air; with none of those either, nothing.
     */
    private static LongSet openCells(Room room, LongSet changed, Function<BlockPos, VoxelShape> shapes) {
        LongSet open = new LongOpenHashSet();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long fallback = 0;
        boolean hasFallback = false;
        for (LongIterator it = room.interior().iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            cursor.set(cell);
            VoxelShape shape = shapes.apply(cursor);
            if (shape != null && RoomScanner.holdsAir(shape)) {
                if (!room.pockets().containsKey(cell) && !changed.contains(cell)) {
                    open.add(cell);
                } else if (!hasFallback) {
                    fallback = cell;
                    hasFallback = true;
                }
            }
        }
        if (open.isEmpty() && hasFallback) {
            open.add(fallback);
        }
        return open;
    }

    /** One side of an opening: the cells of it, and the gas at any one of them. */
    private record Side(LongSet cells, Function<BlockPos, Air> air) {
    }

    private void send(ServerLevel level, List<RoomBreachS2CPacket> puffs) {
        for (RoomBreachS2CPacket puff : puffs) {
            ZPSGamePackets.sendToTrackingChunk(level, new ChunkPos(puff.pos()), puff);
        }
    }

    private Side sideOf(ServerLevel level, Room room) {
        return new Side(room.interior(), pos -> airIn(level, room, pos));
    }

    /**
     * The puffs of gas to draw as two rooms, still apart, become one through the cells of
     * {@code changed} that have opened between them — see {@link #puffs}.
     */
    public List<RoomBreachS2CPacket> mergePuffs(ServerLevel level, Room one, Room other, LongSet changed) {
        return puffs(level, sideOf(level, one), sideOf(level, other), changed);
    }

    /**
     * The puffs of gas to draw as {@code room} takes in {@code space}, sealed cells no room held,
     * through the cells of {@code changed} that have opened onto them — see {@link #puffs}. The
     * space holds the outside air, as a room newly sealed there would.
     */
    public List<RoomBreachS2CPacket> growthPuffs(ServerLevel level, Room room, LongSet space, LongSet changed) {
        if (space.size() <= MERGE_PUFF_MIN_CELLS && room.volume() <= MERGE_PUFF_MIN_CELLS) {
            return List.of();
        }
        Side outside = new Side(space, pos -> new Air(outsideAir(level, pos, 1), AtmosphereExposure.temperatureK(level, pos), space.size()));
        return puffs(level, sideOf(level, room), outside, changed);
    }

    /**
     * The puffs of gas to draw as the gas either side of an opening comes together, one at each
     * cell of {@code changed} that has opened between the two sides. Each is judged where it is,
     * by the gas beside the opening on either side — a big room is not the same all through, and
     * what matters is what is at the door: nothing is drawn unless the pressures there are at least
     * {@link #MERGE_PUFF_PA} apart and the side at the lower is more than
     * {@link #MERGE_PUFF_MIN_CELLS} cells. A puff goes from the higher pressure into the lower,
     * and between them they carry the gas that has to cross for the two to come level.
     */
    private static List<RoomBreachS2CPacket> puffs(ServerLevel level, Side one, Side other, LongSet changed) {
        List<RoomBreachS2CPacket> puffs = new ArrayList<>();
        for (LongIterator it = changed.iterator(); it.hasNext() && puffs.size() < MAX_BREACH_PUFFS; ) {
            BlockPos pos = BlockPos.of(it.nextLong());
            BlockPos besideOne = beside(one.cells(), pos);
            BlockPos besideOther = beside(other.cells(), pos);
            if (besideOne == null || besideOther == null
                    || !RoomScanner.holdsAir(RoomScanner.shapeOf(level, pos, level.getBlockState(pos)))) {
                continue;
            }
            Air oneAir = one.air().apply(besideOne);
            Air otherAir = other.air().apply(besideOther);
            double onePa = pressureOf(oneAir);
            double otherPa = pressureOf(otherAir);
            if (Math.abs(onePa - otherPa) < MERGE_PUFF_PA) {
                continue;
            }
            boolean fromOne = onePa > otherPa;
            Side from = fromOne ? one : other;
            Side into = fromOne ? other : one;
            Air fromAir = fromOne ? oneAir : otherAir;
            Air intoAir = fromOne ? otherAir : oneAir;
            Direction way = wayThrough(from.cells(), into.cells(), pos);
            if (way == null || into.cells().size() <= MERGE_PUFF_MIN_CELLS) {
                continue;
            }
            double cells = one.cells().size() + other.cells().size();
            double levelPa = (onePa * one.cells().size() + otherPa * other.cells().size()) / cells;
            double fromKgPerM3 = fromAir.kgPerM3().values().stream().mapToDouble(Double::doubleValue).sum();
            double crossingKg = fromKgPerM3 * from.cells().size() * (1 - levelPa / Math.max(onePa, otherPa));
            float buoyancy = (float) buoyancy(fromAir.kgPerM3(), fromAir.temperatureK(), intoAir.kgPerM3(), intoAir.temperatureK());
            puffs.add(new RoomBreachS2CPacket(pos, way, (float) crossingKg, buoyancy));
        }
        if (puffs.size() > 1) {
            // The gas crossing is shared between the openings.
            int count = puffs.size();
            puffs.replaceAll(puff -> new RoomBreachS2CPacket(puff.pos(), puff.outward(), puff.massKg() / count, puff.buoyancy()));
        }
        return puffs;
    }

    /** {@code pos} if it is one of {@code cells}, else a cell of them beside it, else null. */
    private static @Nullable BlockPos beside(LongSet cells, BlockPos pos) {
        if (cells.contains(pos.asLong())) {
            return pos;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (cells.contains(neighbour.asLong())) {
                return neighbour;
            }
        }
        return null;
    }

    /**
     * The way gas goes through the opening at {@code pos} from the cells {@code from} into the
     * cells {@code into}: from a side the one is on to the side the other is on. The opening's own
     * cell may be on either side, as a door's is, or neither, as a wall block's was. Null if it
     * does not lie between them.
     */
    private static @Nullable Direction wayThrough(LongSet from, LongSet into, BlockPos pos) {
        boolean fromHere = from.contains(pos.asLong());
        boolean intoHere = into.contains(pos.asLong());
        for (Direction direction : Direction.values()) {
            boolean fromBehind = from.contains(pos.relative(direction.getOpposite()).asLong());
            boolean intoAhead = into.contains(pos.relative(direction).asLong());
            if (fromBehind && (intoAhead || intoHere) || fromHere && intoAhead) {
                return direction;
            }
        }
        return null;
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
        List<Room> saved = new ArrayList<>();
        for (Tag entry : tag.getList("Rooms", Tag.TAG_COMPOUND)) {
            saved.add(Room.fromNbt((CompoundTag) entry));
        }
        // Older saves can hold several rooms over the same air, each with a fill of its own. The
        // newest was the one in use; the rest are dropped, as their gas was counted twice.
        saved.sort(Comparator.comparingInt(Room::id).reversed());
        for (Room room : saved) {
            if (!manager.overlapping(room.interior(), room.pockets()).isEmpty()) {
                ZPSMod.LOGGER.warn("Dropping saved room {}: a newer room holds the same space", room.id());
                continue;
            }
            manager.rooms.put(room.id(), room);
            manager.index(room);
        }
        return manager;
    }
}
