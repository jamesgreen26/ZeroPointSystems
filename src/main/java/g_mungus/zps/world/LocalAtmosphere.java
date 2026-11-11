package g_mungus.zps.world;

import g_mungus.zps.gas.api.HeatSources;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The outside atmosphere at one place: what a biome's {@link AtmosphericComposition} and climate
 * come to there, read smoothly. Biomes are laid out on a grid four blocks apart, and a place takes
 * the air of the eight grid cells around it, each weighed by how near it is, so one biome's air
 * runs into the next's over those four blocks rather than stopping at a line. Deep in one biome
 * every cell agrees and the air is that biome's, exactly.
 *
 * <p>Height is blended as the other two directions are, with each cell read at its own height, so
 * the bands within one biome's composition run into each other the same way.
 *
 * @param shares       the gases there as shares of the whole by weight, {@code zps:vacuum} among
 *                     them, summing to 1: what a living thing breathes
 * @param kgPerM3      the registered gases a cubic metre there holds, in kilograms: each share at
 *                     its own density, thinned with height
 * @param temperatureK the temperature there in kelvin, heat sources included, or
 *                     {@link Double#NaN} where there is nothing exposable
 */
public record LocalAtmosphere(Map<ResourceLocation, Double> shares, Map<ResourceLocation, Double> kgPerM3,
                              double temperatureK) {

    /** Blocks between one cell of the biome grid and the next. */
    private static final int SPACING = 4;
    /** A cell's own air is read at this many blocks into it: its middle, as vanilla's own biome blending has it. */
    private static final int CENTRE = 2;

    /** One cell of the biome grid, as the blend reads it. */
    interface Cell {
        /** Stands for the cell's air: two cells with the same one, read in the same band, need no blending. */
        Object air();

        /** The gases at block height {@code y}, as shares summing to 1. The same map for every height in one band. */
        Map<ResourceLocation, Double> shares(int y);

        /** How thick the air is at block height {@code y}, from 1 down to 0. */
        double thinning(int y);

        /** Stands for the cell's climate: two cells with the same one are as warm as each other everywhere. */
        Object climate();

        /** The temperature the cell's climate gives {@code pos}, in kelvin, or NaN for nothing exposable. */
        double temperatureK(BlockPos pos);
    }

    /** The biome grid, by cell: a block's coordinates over {@link #SPACING}. */
    interface Grid {
        Cell at(int x, int y, int z);
    }

    /** The atmosphere at {@code pos} in {@code level}, a position already in the world rather than on a grid's plot. */
    public static LocalAtmosphere at(Level level, BlockPos pos) {
        int buildTop = level.getMaxBuildHeight();
        return blend(pos, (x, y, z) -> new BiomeCell(level, level.getNoiseBiome(x, y, z).value(), buildTop), LocalAtmosphere::density);
    }

    private static double density(ResourceLocation gas) {
        GasType type = GasTypeRegistry.INSTANCE.getGasType(gas);
        return type == null ? 0 : type.getDensity();
    }

    /**
     * The atmosphere at {@code pos} on {@code grid}.
     *
     * @param density a gas's density in kilograms per cubic metre, or 0 for one that weighs nothing
     */
    static LocalAtmosphere blend(BlockPos pos, Grid grid, ToDoubleFunction<ResourceLocation> density) {
        int x = pos.getX() - CENTRE;
        int y = pos.getY() - CENTRE;
        int z = pos.getZ() - CENTRE;
        int cellX = Math.floorDiv(x, SPACING);
        int cellY = Math.floorDiv(y, SPACING);
        int cellZ = Math.floorDiv(z, SPACING);
        double alongX = Math.floorMod(x, SPACING) / (double) SPACING;
        double alongY = Math.floorMod(y, SPACING) / (double) SPACING;
        double alongZ = Math.floorMod(z, SPACING) / (double) SPACING;

        Cell[] cells = new Cell[8];
        double[] weights = new double[8];
        int[] heights = new int[8];
        int count = 0;
        for (int corner = 0; corner < 8; corner++) {
            int dx = corner & 1;
            int dy = corner >> 1 & 1;
            int dz = corner >> 2 & 1;
            double weight = (dx == 0 ? 1 - alongX : alongX) * (dy == 0 ? 1 - alongY : alongY) * (dz == 0 ? 1 - alongZ : alongZ);
            if (weight > 0) {
                cells[count] = grid.at(cellX + dx, cellY + dy, cellZ + dz);
                weights[count] = weight;
                heights[count] = (cellY + dy) * SPACING + CENTRE;
                count++;
            }
        }

        Cell first = cells[0];
        Map<ResourceLocation, Double> firstShares = first.shares(heights[0]);
        boolean oneAir = true;
        boolean oneClimate = true;
        for (int i = 1; i < count; i++) {
            oneAir &= cells[i].air() == first.air() && cells[i].shares(heights[i]) == firstShares;
            oneClimate &= cells[i].climate() == first.climate();
        }

        Map<ResourceLocation, Double> shares;
        Map<ResourceLocation, Double> kgPerM3 = new HashMap<>();
        if (oneAir) {
            // One band of one composition all round: read where it is asked, as exact as it ever was.
            shares = firstShares;
            addMass(kgPerM3, shares, first.thinning(pos.getY()), density);
        } else {
            shares = new HashMap<>();
            for (int i = 0; i < count; i++) {
                double weight = weights[i];
                Map<ResourceLocation, Double> cellShares = cells[i].shares(heights[i]);
                cellShares.forEach((gas, share) -> shares.merge(gas, share * weight, Double::sum));
                addMass(kgPerM3, cellShares, weight * cells[i].thinning(heights[i]), density);
            }
        }

        double temperatureK;
        if (oneClimate) {
            temperatureK = first.temperatureK(pos);
        } else {
            double kelvin = 0;
            double weighed = 0;
            for (int i = 0; i < count; i++) {
                double cellK = cells[i].temperatureK(pos);
                if (!Double.isNaN(cellK)) {
                    kelvin += cellK * weights[i];
                    weighed += weights[i];
                }
            }
            temperatureK = weighed > 0 ? kelvin / weighed : Double.NaN;
        }
        return new LocalAtmosphere(shares, kgPerM3, temperatureK);
    }

    private static void addMass(Map<ResourceLocation, Double> kgPerM3, Map<ResourceLocation, Double> shares, double scale,
                                ToDoubleFunction<ResourceLocation> density) {
        if (!(scale > 0)) {
            return;
        }
        shares.forEach((gas, share) -> {
            double mass = share * density.applyAsDouble(gas) * scale;
            if (mass > 0) {
                kgPerM3.merge(gas, mass, Double::sum);
            }
        });
    }

    /** A cell of a real level's biome grid. */
    private record BiomeCell(Level level, Biome biome, int buildTop) implements Cell {

        private AtmosphericComposition composition() {
            return AtmosphericComposition.of(biome);
        }

        @Override
        public Object air() {
            return composition();
        }

        @Override
        public Map<ResourceLocation, Double> shares(int y) {
            return composition().at(y);
        }

        @Override
        public double thinning(int y) {
            return composition().density(y, buildTop);
        }

        @Override
        public Object climate() {
            return biome;
        }

        /**
         * Where the composition holds no registered gas there is nothing to carry heat or cold,
         * except {@code zps:vacuum}, which is no Kelvin gas but still exposes what is in it to the
         * biome's temperature. A {@code zps:space} biome is heated by {@link HeatSources}.
         */
        @Override
        public double temperatureK(BlockPos pos) {
            if (!isExposable(composition().at(pos.getY()))) {
                return Double.NaN;
            }
            double kelvin = BiomeTemperature.kelvinAt(biome, pos);
            return SpaceTemperature.appliesTo(biome) ? SpaceTemperature.kelvin(level, pos, kelvin) : kelvin;
        }

        private static boolean isExposable(Map<ResourceLocation, Double> shares) {
            for (ResourceLocation gas : shares.keySet()) {
                if (gas.equals(AtmosphericComposition.VACUUM) || GasTypeRegistry.INSTANCE.getGasType(gas) != null) {
                    return true;
                }
            }
            return false;
        }
    }
}
