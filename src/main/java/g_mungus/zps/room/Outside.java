package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
 * @param massKg       each gas the room's cells would hold of the outside air, in kilograms
 * @param temperatureK the mean of the outside temperature over the room's cells, in kelvin, or
 *                     {@link Double#NaN} where none of them has anything exposable
 */
record Outside(Map<ResourceLocation, Double> massKg, double temperatureK) {

    /**
     * Biomes are laid out in blocks of this many cells a side, so the air is read once for each
     * such block a room reaches into and counted for every cell of the room in it.
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
        Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
        Long2LongMap sampledAt = new Long2LongOpenHashMap();
        for (LongIterator it = interior.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            long block = BlockPos.asLong(BlockPos.getX(cell) >> SAMPLE_SHIFT, BlockPos.getY(cell) >> SAMPLE_SHIFT,
                    BlockPos.getZ(cell) >> SAMPLE_SHIFT);
            if (cells.addTo(block, 1) == 0) {
                sampledAt.put(block, cell);
            }
        }

        Map<ResourceLocation, Double> massKg = new HashMap<>();
        double kelvinSum = 0;
        int kelvinCells = 0;
        for (Long2IntMap.Entry entry : cells.long2IntEntrySet()) {
            BlockPos at = BlockPos.of(sampledAt.get(entry.getLongKey()));
            int count = entry.getIntValue();
            airPerCell.apply(at).forEach((gas, mass) -> massKg.merge(gas, mass * count, Double::sum));
            double kelvin = temperatureK.applyAsDouble(at);
            if (!Double.isNaN(kelvin)) {
                kelvinSum += kelvin * count;
                kelvinCells += count;
            }
        }
        return new Outside(Map.copyOf(massKg), kelvinCells == 0 ? Double.NaN : kelvinSum / kelvinCells);
    }
}
