package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * The outside atmosphere of a whole room: what its cells would hold between them were it not
 * sealed, each read where it is. A room can lie across biomes — a cave partly in vacuum and partly
 * in steam — so the outside is not one place's air: it is every cell's own, added up.
 *
 * <p>Nor is the room's own gas one mix. What a room holds is the outside's, less what has been
 * taken out and plus what has been put in, and each place in it has its own outside air with that
 * difference applied ({@link #airAt}, {@link #temperatureAt}): a room that has just sealed, or has
 * leaked all the way back, is at every spot exactly the air that was there anyway.
 *
 * @param massKg       each gas the room's cells would hold of the outside air, in kilograms
 * @param temperatureK the mean of the outside temperature over the room's cells, in kelvin, or
 *                     {@link Double#NaN} where none of them has anything exposable
 */
record Outside(Map<ResourceLocation, Double> massKg, double temperatureK) {

    /**
     * Biomes are laid out in blocks of this many cells a side, so the air is read once for each
     * such block a room reaches into, in the middle of the room's cells there, and counted for
     * every one of them.
     */
    private static final int SAMPLE_SHIFT = 2;

    /**
     * The outside of a room whose cells are {@code interior}.
     *
     * @param airPerCell   what one cell at a position holds of the outside air, in kilograms by gas
     * @param temperatureK the outside temperature at a position, in kelvin, or NaN for none
     */
    static Outside of(LongSet interior, Function<BlockPos, Map<ResourceLocation, Double>> airPerCell,
                      ToDoubleFunction<BlockPos> temperatureK) {
        // For each block of cells: how many of the room's are in it, and the sum of where they are.
        Long2ObjectMap<long[]> blocks = new Long2ObjectOpenHashMap<>();
        for (LongIterator it = interior.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            long[] block = blocks.computeIfAbsent(BlockPos.asLong(x >> SAMPLE_SHIFT, y >> SAMPLE_SHIFT, z >> SAMPLE_SHIFT),
                    ignored -> new long[4]);
            block[0]++;
            block[1] += x;
            block[2] += y;
            block[3] += z;
        }

        Map<ResourceLocation, Double> massKg = new HashMap<>();
        double kelvinSum = 0;
        int kelvinCells = 0;
        for (long[] block : blocks.values()) {
            int count = (int) block[0];
            BlockPos at = new BlockPos((int) Math.floorDiv(block[1], count), (int) Math.floorDiv(block[2], count),
                    (int) Math.floorDiv(block[3], count));
            airPerCell.apply(at).forEach((gas, mass) -> massKg.merge(gas, mass * count, Double::sum));
            double kelvin = temperatureK.applyAsDouble(at);
            if (!Double.isNaN(kelvin)) {
                kelvinSum += kelvin * count;
                kelvinCells += count;
            }
        }
        return new Outside(Map.copyOf(massKg), kelvinCells == 0 ? Double.NaN : kelvinSum / kelvinCells);
    }

    /**
     * What one cell of a room holds, in kilograms by gas, where the outside air there is
     * {@code localKg}. A gas the room is short of is short by the same share everywhere: a room
     * with half the steam its cells had has half of each cell's own. A gas it has more of than
     * the outside gave it has the surplus spread evenly on top of each cell's own. Either way the
     * cells add up to what the room holds.
     *
     * @param roomKg  what the whole room holds
     * @param volume  the room's cells
     * @param localKg what one cell here holds of the outside air
     */
    Map<ResourceLocation, Double> airAt(Map<ResourceLocation, Double> roomKg, int volume, Map<ResourceLocation, Double> localKg) {
        Map<ResourceLocation, Double> air = new HashMap<>();
        localKg.forEach((gas, local) -> {
            double held = roomKg.getOrDefault(gas, 0.0);
            double outside = massKg.getOrDefault(gas, 0.0);
            if (held < outside) {
                air.put(gas, local * held / outside);
            } else {
                air.put(gas, local);
            }
        });
        roomKg.forEach((gas, held) -> {
            double surplus = held - massKg.getOrDefault(gas, 0.0);
            if (surplus > 0) {
                air.merge(gas, surplus / Math.max(1, volume), Double::sum);
            }
        });
        air.values().removeIf(mass -> !(mass > 0));
        return air;
    }

    /**
     * The temperature at a place in a room at {@code roomK}, where the outside there is at
     * {@code localK}: as far from the outside's there as the room is from its outside's overall.
     * A room with no temperature has none anywhere, and where the outside has none to go by the
     * room's stands as it is.
     */
    double temperatureAt(double roomK, double localK) {
        if (Double.isNaN(roomK) || Double.isNaN(localK) || Double.isNaN(temperatureK)) {
            return roomK;
        }
        return Math.max(0, localK + roomK - temperatureK);
    }
}
