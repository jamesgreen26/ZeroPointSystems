package g_mungus.zps.room;

import g_mungus.zps.gas.GasExposure;
import g_mungus.zps.world.AtmosphericComposition;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One sealed room and the gas in it. The gas is a plain store: masses by gas id and one
 * temperature. Nothing pushes back on what feeds it, but it slowly leaks back to the outside
 * atmosphere through its walls — see {@link #leak}.
 */
public final class Room {
    private final int id;
    private LongSet interior;
    private LongSet boundary;
    /** Which pockets of the cells only partly in the room are; see {@link RoomScan#pockets()}. */
    private Long2LongMap pockets;
    private final Map<ResourceLocation, Double> massKg = new HashMap<>();
    private double temperatureK;
    /** Where the outside atmosphere the room leaks toward is read: a cell of its interior. */
    private BlockPos anchor;
    /**
     * Game time the room went dormant, or {@link #ACTIVE}. A dormant room has caught up with the
     * outside: it holds no gas of its own and replaces nothing, and is only kept so the next gas in
     * can wake it without searching for its walls again.
     */
    private long dormantSince = ACTIVE;
    private static final long ACTIVE = -1;

    /**
     * Gas fed in or drawn out since the last leak, and the running average of that per tick, in
     * kilograms. Either way it is held off the outside, so a room being drained stays awake as one
     * being filled does.
     */
    private double fedThisTick;
    private double inflowKgPerTick;
    /** How much of each tick's feed goes into the running average: about a second's memory. */
    private static final double INFLOW_SMOOTHING = 0.05;

    Room(int id, RoomScan scan, BlockPos anchor, double temperatureK) {
        this.id = id;
        this.interior = scan.interior();
        this.boundary = scan.boundary();
        this.pockets = scan.pockets();
        this.anchor = anchor.immutable();
        this.temperatureK = temperatureK;
    }

    public int id() {
        return id;
    }

    public LongSet interior() {
        return interior;
    }

    public LongSet boundary() {
        return boundary;
    }

    public int volume() {
        return interior.size();
    }

    /** The room's surface, in blocks: every wall block air inside touches. */
    public int surfaceArea() {
        return boundary.size();
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** Whether the room holds gas of its own and replaces the outside atmosphere. */
    public boolean isActive() {
        return dormantSince == ACTIVE;
    }

    long dormantSince() {
        return dormantSince;
    }

    /** Caught up with the outside: let the gas go and stop replacing anything. */
    void sleep(long gameTime) {
        massKg.clear();
        dormantSince = gameTime;
    }

    /** Gas is going in again: start over from the outside air, as a newly sealed room does. */
    void wake(Map<ResourceLocation, Double> outsideKg, double outsideK) {
        dormantSince = ACTIVE;
        temperatureK = outsideK;
        add(outsideKg, outsideK);
    }

    public double temperatureK() {
        return temperatureK;
    }

    public Map<ResourceLocation, Double> massKg() {
        return Collections.unmodifiableMap(massKg);
    }

    public double totalMassKg() {
        return massKg.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    Long2LongMap pockets() {
        return pockets;
    }

    void reshape(RoomScan scan) {
        this.interior = scan.interior();
        this.boundary = scan.boundary();
        this.pockets = scan.pockets();
        if (!interior.contains(anchor.asLong())) {
            anchor = BlockPos.of(interior.iterator().nextLong());
        }
    }

    /** Take in what {@link RoomScanner#grow} found. */
    void grow(LongSet added, LongSet newBoundary, Long2LongMap pocketsAfter) {
        interior.addAll(added);
        boundary.addAll(newBoundary);
        boundary.removeAll(added);
        for (Long2LongMap.Entry entry : pocketsAfter.long2LongEntrySet()) {
            if (entry.getLongValue() == RoomScanner.WHOLE) {
                pockets.remove(entry.getLongKey());
            } else {
                pockets.put(entry.getLongKey(), entry.getLongValue());
            }
        }
    }

    /**
     * Move {@code fraction} of the way back to the outside: each gas toward what the room would
     * hold of the outside air, {@code outsideKg}, and the temperature toward {@code outsideK}.
     * Gases the outside has none of drain away; ones it has seep back in.
     */
    void leak(Map<ResourceLocation, Double> outsideKg, double outsideK, double fraction) {
        inflowKgPerTick += (fedThisTick - inflowKgPerTick) * INFLOW_SMOOTHING;
        fedThisTick = 0;

        Set<ResourceLocation> gases = new HashSet<>(massKg.keySet());
        gases.addAll(outsideKg.keySet());
        for (ResourceLocation gas : gases) {
            double mass = massKg.getOrDefault(gas, 0.0);
            double next = mass + (outsideKg.getOrDefault(gas, 0.0) - mass) * fraction;
            if (next < GasExposure.MIN_GAS_KG * 1e-3) {
                massKg.remove(gas);
            } else {
                massKg.put(gas, next);
            }
        }
        if (!Double.isNaN(outsideK)) {
            temperatureK = Double.isNaN(temperatureK) ? outsideK : temperatureK + (outsideK - temperatureK) * fraction;
        }
    }

    /**
     * Whether the room has as good as returned to the outside and will stay there: within a
     * percent of the outside air's mass, give or take a trace, and within a kelvin of its
     * temperature — and what is being fed in would not hold it any further off either. A steady
     * supply settles where it matches the leak, {@code inflow / fraction} above the outside, so a
     * trickle into a big room never counts for anything, however long it runs.
     *
     * @param fraction the share of the way back to the outside the room leaks each tick
     */
    boolean hasSettled(Map<ResourceLocation, Double> outsideKg, double outsideK, double fraction) {
        Set<ResourceLocation> gases = new HashSet<>(massKg.keySet());
        gases.addAll(outsideKg.keySet());
        double difference = 0;
        double outsideTotal = 0;
        for (ResourceLocation gas : gases) {
            double outside = outsideKg.getOrDefault(gas, 0.0);
            difference += Math.abs(massKg.getOrDefault(gas, 0.0) - outside);
            outsideTotal += outside;
        }
        double tolerance = 0.01 * outsideTotal + GasExposure.MIN_GAS_KG;
        double steady = fraction > 0 ? inflowKgPerTick / fraction : (inflowKgPerTick > 0 ? Double.POSITIVE_INFINITY : 0);
        if (difference > tolerance || steady > tolerance) {
            return false;
        }
        return Double.isNaN(outsideK) || Double.isNaN(temperatureK) || Math.abs(temperatureK - outsideK) < 1.0;
    }

    /** Let {@code kg} of the mix out, every gas in proportion to how much of it there is. */
    void release(double kg) {
        double total = totalMassKg();
        if (total <= 0 || kg <= 0) {
            return;
        }
        double keep = Math.max(0, 1 - kg / total);
        massKg.replaceAll((gas, mass) -> mass * keep);
    }

    /**
     * Take {@code kg} of each gas out, or as much as there is, and count it toward the room's
     * supply. The temperature stays as it is: the whole mix shares one.
     *
     * @return what was actually taken, in kilograms
     */
    Map<ResourceLocation, Double> draw(Map<ResourceLocation, Double> kg) {
        Map<ResourceLocation, Double> taken = new HashMap<>();
        kg.forEach((gas, wanted) -> {
            double held = massKg.getOrDefault(gas, 0.0);
            double take = Math.min(held, Math.max(0, wanted));
            if (take <= 0) {
                return;
            }
            if (held - take < GasExposure.MIN_GAS_KG * 1e-3) {
                massKg.remove(gas);
            } else {
                massKg.put(gas, held - take);
            }
            taken.put(gas, take);
            fedThisTick += take;
        });
        return taken;
    }

    /** Gas let in from outside the room: mixed in, and counted toward its supply. */
    void feed(Map<ResourceLocation, Double> kg, double temperatureK) {
        add(kg, temperatureK);
        fedThisTick += kg.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Mix {@code kg} in at {@code temperatureK}. The temperature settles where the heat of both
     * parts balances, weighted by each gas's specific heat; a room with nothing in it takes the
     * incoming temperature outright.
     */
    void add(Map<ResourceLocation, Double> kg, double temperatureK) {
        double heldCapacity = heatCapacity(massKg);
        double addedCapacity = heatCapacity(kg);
        kg.forEach((gas, mass) -> {
            if (mass > 0) {
                massKg.merge(gas, mass, Double::sum);
            }
        });
        if (Double.isNaN(temperatureK) || addedCapacity <= 0) {
            return;
        }
        if (Double.isNaN(this.temperatureK) || heldCapacity <= 0) {
            this.temperatureK = temperatureK;
            return;
        }
        this.temperatureK = (heldCapacity * this.temperatureK + addedCapacity * temperatureK)
                / (heldCapacity + addedCapacity);
    }

    /** Take in everything another room held, and its supply, as when the wall between them comes down. */
    void absorb(Room other) {
        add(other.massKg, other.temperatureK);
        fedThisTick += other.fedThisTick;
        inflowKgPerTick += other.inflowKgPerTick;
    }

    /**
     * The gas a living thing inside breathes, as weights by mass. Traces do not count; a room
     * holding nothing beyond them is vacuum.
     */
    public Map<ResourceLocation, Double> gases() {
        Map<ResourceLocation, Double> gases = new HashMap<>();
        massKg.forEach((gas, mass) -> {
            if (mass >= GasExposure.MIN_GAS_KG) {
                gases.put(gas, mass);
            }
        });
        if (gases.isEmpty()) {
            gases.put(AtmosphericComposition.VACUUM, 1.0);
        }
        return gases;
    }

    /** Joules per kelvin; gases no mod registers carry no heat. */
    private static double heatCapacity(Map<ResourceLocation, Double> kg) {
        double capacity = 0;
        for (Map.Entry<ResourceLocation, Double> entry : kg.entrySet()) {
            GasType type = GasTypeRegistry.INSTANCE.getGasType(entry.getKey());
            if (type != null && entry.getValue() > 0) {
                capacity += entry.getValue() * type.getSpecificHeatCapacity();
            }
        }
        return capacity;
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putLongArray("Interior", interior.toLongArray());
        tag.putLongArray("Boundary", boundary.toLongArray());
        tag.putDouble("TemperatureK", temperatureK);
        tag.putLong("Anchor", anchor.asLong());
        tag.putLongArray("PocketCells", pockets.keySet().toLongArray());
        tag.putLongArray("PocketMasks", pockets.values().toLongArray());
        CompoundTag gases = new CompoundTag();
        massKg.forEach((gas, mass) -> gases.putDouble(gas.toString(), mass));
        tag.put("Gases", gases);
        return tag;
    }

    static Room fromNbt(CompoundTag tag) {
        LongSet interior = new LongOpenHashSet(tag.getLongArray("Interior"));
        long anchor = tag.contains("Anchor") ? tag.getLong("Anchor") : interior.iterator().nextLong();
        Long2LongMap pockets = new Long2LongOpenHashMap();
        long[] pocketCells = tag.getLongArray("PocketCells");
        long[] pocketMasks = tag.getLongArray("PocketMasks");
        for (int i = 0; i < Math.min(pocketCells.length, pocketMasks.length); i++) {
            pockets.put(pocketCells[i], pocketMasks[i]);
        }
        Room room = new Room(tag.getInt("Id"),
                new RoomScan(interior, new LongOpenHashSet(tag.getLongArray("Boundary")), pockets),
                BlockPos.of(anchor), tag.getDouble("TemperatureK"));
        CompoundTag gases = tag.getCompound("Gases");
        for (String key : gases.getAllKeys()) {
            ResourceLocation gas = ResourceLocation.tryParse(key);
            if (gas != null) {
                room.massKg.put(gas, gases.getDouble(key));
            }
        }
        return room;
    }
}
