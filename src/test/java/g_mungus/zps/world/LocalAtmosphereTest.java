package g_mungus.zps.world;

import g_mungus.zps.testutil.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The atmosphere read on a made-up biome grid: one biome's air deep inside it, and a smooth run
 * from one to the next over the four blocks between two cells' middles.
 */
public class LocalAtmosphereTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    private static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath("kelvin", "air");
    private static final ResourceLocation STEAM = ResourceLocation.fromNamespaceAndPath("vs_clockwork", "steam");
    private static final ResourceLocation VACUUM = ResourceLocation.fromNamespaceAndPath("zps", "vacuum");
    private static final ToDoubleFunction<ResourceLocation> DENSITY = gas -> gas.equals(AIR) ? 1.2 : gas.equals(STEAM) ? 0.6 : 0;

    /** A biome with one band of air at every height, as warm as it is told. */
    private record Plain(Map<ResourceLocation, Double> gases, double kelvin) implements LocalAtmosphere.Cell {
        @Override
        public Object air() {
            return gases;
        }

        @Override
        public Map<ResourceLocation, Double> shares(int y) {
            return gases;
        }

        @Override
        public double thinning(int y) {
            return 1;
        }

        @Override
        public Object climate() {
            return this;
        }

        @Override
        public double temperatureK(BlockPos pos) {
            return kelvin;
        }
    }

    private static final Plain STEAMY = new Plain(Map.of(STEAM, 0.8, AIR, 0.2), 273.0);
    private static final Plain EMPTY = new Plain(Map.of(VACUUM, 1.0), 201.0);

    /** Steam in every cell west of x = 0, vacuum from there east: the cells' middles are at x = -2 and x = 2. */
    private static final LocalAtmosphere.Grid BORDER = (x, y, z) -> x < 0 ? STEAMY : EMPTY;

    private static LocalAtmosphere at(int x) {
        return LocalAtmosphere.blend(new BlockPos(x, 64, 9), BORDER, DENSITY);
    }

    @Test
    void deepInOneBiome_theAirIsThatBiomes() {
        LocalAtmosphere steam = at(-20);
        assertSame(STEAMY.air(), steam.shares());
        assertEquals(0.8 * 0.6, steam.kgPerM3().get(STEAM), 1e-9);
        assertEquals(0.2 * 1.2, steam.kgPerM3().get(AIR), 1e-9);
        assertEquals(273.0, steam.temperatureK(), 1e-9);

        LocalAtmosphere vacuum = at(20);
        assertSame(EMPTY.air(), vacuum.shares());
        assertTrue(vacuum.kgPerM3().isEmpty());
        assertEquals(201.0, vacuum.temperatureK(), 1e-9);
    }

    /** From the middle of the last steam cell to the middle of the first vacuum one, a quarter at a time. */
    @Test
    void acrossABorder_oneBiomesAirRunsIntoTheNexts() {
        for (int x = -2; x <= 2; x++) {
            double steamy = (2 - x) / 4.0;
            LocalAtmosphere air = at(x);
            assertEquals(0.8 * steamy, air.shares().getOrDefault(STEAM, 0.0), 1e-9, "steam share at x=" + x);
            assertEquals(1 - steamy, air.shares().getOrDefault(VACUUM, 0.0), 1e-9, "vacuum share at x=" + x);
            assertEquals(0.8 * 0.6 * steamy, air.kgPerM3().getOrDefault(STEAM, 0.0), 1e-9, "steam at x=" + x);
            assertEquals(273.0 * steamy + 201.0 * (1 - steamy), air.temperatureK(), 1e-9, "temperature at x=" + x);
        }
    }

    @Test
    void shares_alwaysSumToOne() {
        for (int x = -6; x <= 6; x++) {
            assertEquals(1.0, at(x).shares().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9, "at x=" + x);
        }
    }

    /** Two biomes with the same air but their own climates: the air is exact, only the warmth blends. */
    @Test
    void sameAirInTwoClimates_blendsOnlyTheTemperature() {
        Map<ResourceLocation, Double> plainAir = Map.of(AIR, 1.0);
        Plain warm = new Plain(plainAir, 300.0);
        Plain cold = new Plain(plainAir, 260.0);
        LocalAtmosphere air = LocalAtmosphere.blend(new BlockPos(0, 64, 0), (x, y, z) -> x < 0 ? warm : cold, DENSITY);

        assertSame(plainAir, air.shares());
        assertEquals(1.2, air.kgPerM3().get(AIR), 1e-9);
        assertEquals(280.0, air.temperatureK(), 1e-9);
    }

    /** A cell with nothing exposable has no say in the temperature next to one that has. */
    @Test
    void cellsWithNoTemperature_areLeftOutOfIt() {
        Plain none = new Plain(Map.of(VACUUM, 1.0), Double.NaN);
        assertEquals(273.0, LocalAtmosphere.blend(new BlockPos(0, 64, 0), (x, y, z) -> x < 0 ? STEAMY : none, DENSITY).temperatureK(), 1e-9);
        assertTrue(Double.isNaN(LocalAtmosphere.blend(new BlockPos(0, 64, 0), (x, y, z) -> none, DENSITY).temperatureK()));
    }

    /** One biome with air below y = 100 and vacuum from there up, thinning to nothing on the way. */
    private static final LocalAtmosphere.Cell BANDED = new LocalAtmosphere.Cell() {
        private final Map<ResourceLocation, Double> below = Map.of(AIR, 1.0);
        private final Map<ResourceLocation, Double> above = Map.of(VACUUM, 1.0);

        @Override
        public Object air() {
            return this;
        }

        @Override
        public Map<ResourceLocation, Double> shares(int y) {
            return y < 100 ? below : above;
        }

        @Override
        public double thinning(int y) {
            return Math.max(0, Math.min(1, (100 - y) / 100.0));
        }

        @Override
        public Object climate() {
            return this;
        }

        @Override
        public double temperatureK(BlockPos pos) {
            return 250.0;
        }
    };

    private static LocalAtmosphere banded(int y) {
        return LocalAtmosphere.blend(new BlockPos(5, y, 5), (cx, cy, cz) -> BANDED, DENSITY);
    }

    /** Within a band the thinning is read at the very height asked, not at the cells' middles. */
    @Test
    void withinOneBand_theThinningIsExact() {
        assertEquals(1.2 * 0.37, banded(63).kgPerM3().get(AIR), 1e-9);
        assertEquals(1.2 * 0.5, banded(50).kgPerM3().get(AIR), 1e-9);
    }

    /** The cells' middles either side of the band's edge are at y = 98 and y = 102. */
    @Test
    void acrossABandsEdge_oneBandRunsIntoTheNext() {
        assertEquals(1.0, banded(98).shares().getOrDefault(AIR, 0.0), 1e-9);
        assertEquals(0.5, banded(100).shares().getOrDefault(AIR, 0.0), 1e-9);
        assertEquals(0.5, banded(100).shares().getOrDefault(VACUUM, 0.0), 1e-9);
        assertEquals(0.25, banded(101).shares().getOrDefault(AIR, 0.0), 1e-9);
        assertEquals(1.0, banded(102).shares().getOrDefault(VACUUM, 0.0), 1e-9);
    }

    /** Cells are found the same way either side of zero. */
    @Test
    void belowZero_theGridIsReadTheSameWay() {
        LocalAtmosphere.Grid grid = (x, y, z) -> y < -10 ? STEAMY : EMPTY;
        // Cell y = -10 is blocks -40..-37, its middle at -38; the one below it has its middle at -42.
        assertEquals(0.8, LocalAtmosphere.blend(new BlockPos(0, -42, 0), grid, DENSITY).shares().get(STEAM), 1e-9);
        assertEquals(0.4, LocalAtmosphere.blend(new BlockPos(0, -40, 0), grid, DENSITY).shares().get(STEAM), 1e-9);
        assertEquals(1.0, LocalAtmosphere.blend(new BlockPos(0, -38, 0), grid, DENSITY).shares().get(VACUUM), 1e-9);
    }
}
