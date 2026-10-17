package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.networking.RoomBreachS2CPacket;
import g_mungus.zps.world.AtmosphericComposition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import g_mungus.zps.networking.ZPSGamePackets;
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
 */
public final class RoomManager extends SavedData {

    private static final String KEY = "zps_rooms";

    /** How long a position that failed to seal is left alone before gas there looks again, in ticks. */
    static final int FAILED_SCAN_COOLDOWN = 100;
    /** How long a dormant room is remembered with nothing going in, in ticks. */
    public static final int DORMANT_TICKS = 1200;
    /** From this pressure up, a room lets out some of what is put into it. */
    public static final double RELEASE_START_PA = 1_000_000.0;
    /** From this pressure up, a room lets out as much gas as is put into it. */
    public static final double MAX_PRESSURE_PA = 10_000_000.0;
    /** Most breach puffs drawn for one room coming open. */
    private static final int MAX_BREACH_PUFFS = 4;

    private final Int2ObjectMap<Room> rooms = new Int2ObjectOpenHashMap<>();
    private final Long2IntMap byInterior = new Long2IntOpenHashMap();
    private final Long2ObjectMap<IntList> byBoundary = new Long2ObjectOpenHashMap<>();
    private int nextId = 1;

    /** Start positions whose last scan found no room, and the game time they may be scanned again. */
    private final Long2LongMap failedUntil = new Long2LongOpenHashMap();
    /** Rooms with a changed block since the last tick, and which positions changed. */
    private final Int2ObjectMap<LongSet> dirty = new Int2ObjectOpenHashMap<>();

    public RoomManager() {
        byInterior.defaultReturnValue(-1);
    }

    public static RoomManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RoomManager::load, RoomManager::new, KEY);
    }

    // --- lookup ------------------------------------------------------------------------------

    /** The active room {@code pos} is inside, or null. Dormant rooms replace nothing, so they are left out. */
    public @Nullable Room roomAt(BlockPos pos) {
        Room room = knownRoomAt(pos);
        return room != null && room.isActive() ? room : null;
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

        Room room = knownRoomAt(at);
        if (room == null) {
            room = seal(level, at);
            if (room == null) {
                return;
            }
        } else if (!room.isActive()) {
            room.wake(outsideAir(level, room.anchor(), room.volume()), AtmosphereExposure.temperatureK(level, room.anchor()));
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

        RoomScan scan = RoomScanner.scan(at, RoomScanner.shapes(level), ZPSConfig.roomMaxVolume());
        if (scan == null) {
            if (failedUntil.size() > 4096) {
                failedUntil.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
            }
            failedUntil.put(key, now + FAILED_SCAN_COOLDOWN);
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
     * gases no mod registers weigh nothing.
     */
    static Map<ResourceLocation, Double> outsideAir(ServerLevel level, BlockPos at, int volume) {
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

    // --- changes -----------------------------------------------------------------------------

    /**
     * A block changed. If it is part of a room and its shape changed, the room is checked again at
     * the end of the tick.
     */
    public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        long key = pos.asLong();
        int interior = byInterior.get(key);
        IntList bounding = byBoundary.get(key);
        if (interior < 0 && bounding == null) {
            return;
        }
        VoxelShape before = RoomScanner.shapeOf(level, pos, oldState);
        VoxelShape after = RoomScanner.shapeOf(level, pos, newState);
        if (before == after || !Shapes.joinIsNotEmpty(before, after, BooleanOp.NOT_SAME)) {
            return;
        }
        if (interior >= 0) {
            markDirty(interior, key);
        }
        if (bounding != null) {
            for (int i = 0; i < bounding.size(); i++) {
                markDirty(bounding.getInt(i), key);
            }
        }
    }

    private void markDirty(int id, long pos) {
        dirty.computeIfAbsent(id, ignored -> new LongOpenHashSet()).add(pos);
    }

    /** Check every room that had a block change this tick, then let every loaded room leak. */
    public void tick(ServerLevel level) {
        recheckChanged(level);
        leak(level);
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
        dirty.clear();
        for (Int2ObjectMap.Entry<LongSet> entry : changed.int2ObjectEntrySet()) {
            Room room = rooms.get(entry.getIntKey());
            if (room == null) {
                continue;
            }
            if (room.isActive()) {
                recheck(level, room, entry.getValue());
            } else {
                // Nothing in it to lose; the next gas in will look for walls afresh.
                dissolve(room);
            }
        }
    }

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

        BlockPos start = openCell(room, shapes);
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

    /** Any interior cell air can still be in, to fill from. */
    private static @Nullable BlockPos openCell(Room room, Function<BlockPos, VoxelShape> shapes) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (LongIterator it = room.interior().iterator(); it.hasNext(); ) {
            cursor.set(it.nextLong());
            VoxelShape shape = shapes.apply(cursor);
            if (shape != null && RoomScanner.holdsAir(shape)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Air got out: draw it going and forget the room. */
    private void breach(ServerLevel level, Room room, LongSet changed) {
        ResourceLocation gas = room.dominantGas();
        if (gas != null) {
            List<BlockPos> holes = new ArrayList<>();
            List<Direction> outwards = new ArrayList<>();
            for (LongIterator it = changed.iterator(); it.hasNext() && holes.size() < MAX_BREACH_PUFFS; ) {
                BlockPos pos = BlockPos.of(it.nextLong());
                Direction outward = outwardFrom(room, pos);
                if (outward != null && RoomScanner.holdsAir(RoomScanner.shapeOf(level, pos, level.getBlockState(pos)))) {
                    holes.add(pos);
                    outwards.add(outward);
                }
            }
            float perPuff = holes.isEmpty() ? 0 : (float) (room.totalMassKg() / holes.size());
            for (int i = 0; i < holes.size(); i++) {
                ZPSGamePackets.sendToTrackingChunk(level, new ChunkPos(holes.get(i)),
                        new RoomBreachS2CPacket(holes.get(i), outwards.get(i), gas, perPuff));
            }
        }
        ZPSMod.LOGGER.debug("Room {} breached", room.id());
        dissolve(room);
    }

    /** The way out of the room through {@code pos}: away from the interior cell beside it. */
    private static @Nullable Direction outwardFrom(Room room, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (room.interior().contains(pos.relative(direction).asLong())) {
                return direction.getOpposite();
            }
        }
        return null;
    }

    /** Forget a room and everything in it. The blocks are untouched. */
    public void dissolve(Room room) {
        if (rooms.remove(room.id()) == null) {
            return;
        }
        unindex(room);
        dirty.remove(room.id());
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
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
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
    public static RoomManager load(CompoundTag tag) {
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
