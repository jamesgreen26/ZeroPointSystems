package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.gas.api.GridTransfer;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.List;
import java.util.Map;

/**
 * Reactors and rooms carried between levels by {@link GridTransfer}, as when a ship changes
 * dimension. The tests detach and attach within the test level, which goes through the same steps
 * as two levels would; what differs between levels is only the dimension in the chamber's Kelvin
 * position, which both steps take from the level they are given.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridTransferGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";

    /** One 5x5x5 shell around a 3x3x3 cavity, in the middle of the arena. */
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);
    private static final BlockPos HOST = MIN.offset(1, 1, 1);
    private static final BlockPos CENTRE = new BlockPos(5, 3, 5);
    private static final BlockPos WEST_WALL = new BlockPos(3, 3, 5);
    private static final BlockPos EAST_WALL = new BlockPos(7, 3, 5);

    /** Two shells side by side, for a move by {@link #SHIFT}: the west one and where it goes. */
    private static final BlockPos WEST_MIN = new BlockPos(0, 1, 3);
    private static final BlockPos WEST_MAX = new BlockPos(4, 5, 7);
    private static final BlockPos SHIFT = new BlockPos(6, 0, 0);

    private static final double CHAMBER_K = 5_000.0;
    private static final double FLUX_KG = 0.01;

    // --- helpers ------------------------------------------------------------------------------

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static ReactorManager reactors(GameTestHelper helper) {
        return ReactorManager.get(helper.getLevel());
    }

    private static RoomManager rooms(GameTestHelper helper) {
        return RoomManager.get(helper.getLevel());
    }

    private static GridTransfer.Region box(GameTestHelper helper, BlockPos min, BlockPos max) {
        return GridTransfer.Region.box(BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(max)));
    }

    private static List<Reactor> reactorsAt(GameTestHelper helper, BlockPos relative) {
        return reactors(helper).reactorsAt(helper.absolutePos(relative));
    }

    private static Reactor onlyReactorAt(GameTestHelper helper, BlockPos relative) {
        List<Reactor> found = reactorsAt(helper, relative);
        if (found.size() != 1) {
            helper.fail("Expected exactly one reactor at " + relative + ", found " + found.size());
            throw new IllegalStateException();
        }
        return found.get(0);
    }

    private static void buildReactorShell(GameTestHelper helper, BlockPos min, BlockPos max) {
        BlockState plating = ModBlocks.REINFORCED_PLATING.get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean edge = pos.getX() == min.getX() || pos.getX() == max.getX()
                    || pos.getY() == min.getY() || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            if (edge) {
                helper.setBlock(pos, plating);
            }
        }
    }

    /** Some gas in the chamber at {@code host}, then the chamber brought to {@code temperature}. */
    private static void fillChamber(GameTestHelper helper, BlockPos host, double temperature) {
        DuctNodePos node = node(helper, host);
        kelvin().addGasAtTemperature(node, ModGases.FLUX, FLUX_KG, 300.0);
        double current = kelvin().getTemperatureAt(node);
        kelvin().modHeatEnergy(node, (temperature - current) * kelvin().getNodeHeatCapacity(node));
    }

    private static double totalMass(GameTestHelper helper, BlockPos host) {
        double total = 0;
        for (double mass : kelvin().getGasMassAt(node(helper, host)).values()) {
            total += mass;
        }
        return total;
    }

    private static void assertChamber(GameTestHelper helper, BlockPos host, double mass, double temperature) {
        helper.assertTrue(kelvin().getNodeAt(node(helper, host)) instanceof ReactorChamberNode,
                "The chamber should be back in Kelvin at " + host);
        double massNow = totalMass(helper, host);
        double temperatureNow = kelvin().getTemperatureAt(node(helper, host));
        helper.assertTrue(Math.abs(massNow - mass) < 1e-9,
                "The chamber should hold the " + mass + " kg it left with, holds " + massNow);
        helper.assertTrue(Math.abs(temperatureNow - temperature) < 1.0,
                "The chamber should be at the " + temperature + " K it left at, is " + temperatureNow);
    }

    private static void buildRoomShell(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean wall = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            helper.setBlock(pos, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        // The test world is reused between runs, and a room saved by an earlier run would still be here.
        Room stale = rooms(helper).knownRoomAt(helper.absolutePos(CENTRE));
        if (stale != null) {
            rooms(helper).dissolve(stale);
        }
    }

    private static void addFlux(GameTestHelper helper, double kg) {
        Map<GasType, Double> gas = Map.of(ModGases.FLUX, kg);
        rooms(helper).addGas(helper.getLevel(), helper.absolutePos(CENTRE), gas, 300.0);
    }

    // --- reactors -----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void reactorLeavesWithItsChamberAndComesBack(GameTestHelper helper) {
        buildReactorShell(helper, MIN, MAX);
        fillChamber(helper, HOST, CHAMBER_K);
        int before = onlyReactorAt(helper, WEST_WALL).id();
        double mass = totalMass(helper, HOST);
        double temperature = kelvin().getTemperatureAt(node(helper, HOST));

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX));
        helper.assertTrue(cargo.reactorCount() == 1, "The reactor should be taken, took " + cargo.reactorCount());
        helper.assertTrue(reactorsAt(helper, WEST_WALL).isEmpty(), "The level should no longer list the reactor");
        helper.assertTrue(kelvin().getNodeAt(node(helper, HOST)) == null,
                "The chamber should be out of Kelvin until it arrives");

        cargo.attach(helper.getLevel(), BlockPos.ZERO);
        Reactor after = onlyReactorAt(helper, WEST_WALL);
        helper.assertTrue(after.id() != before, "The arrival should have an id of the level's own");
        helper.assertTrue(after.host().equals(helper.absolutePos(HOST)), "The host should be where it was");
        helper.assertTrue(after.volume() == 27, "The cavity should be the same 27 cells, is " + after.volume());
        assertChamber(helper, HOST, mass, temperature);
        helper.succeed();
    }

    /**
     * Moved by an offset, into a shell the level has already found a reactor in, as a ship's would
     * be if it had been there before: the arrival takes its place, contents and all.
     */
    @GameTest(template = TEMPLATE)
    public static void reactorArrivesShiftedOverAStaleOne(GameTestHelper helper) {
        buildReactorShell(helper, WEST_MIN, WEST_MAX);
        BlockPos westHost = WEST_MIN.offset(1, 1, 1);
        fillChamber(helper, westHost, CHAMBER_K);
        double mass = totalMass(helper, westHost);
        double temperature = kelvin().getTemperatureAt(node(helper, westHost));

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, WEST_MIN, WEST_MAX));
        helper.assertTrue(cargo.reactorCount() == 1, "The west reactor should be taken, took " + cargo.reactorCount());

        BlockPos eastMin = WEST_MIN.offset(SHIFT);
        BlockPos eastMax = WEST_MAX.offset(SHIFT);
        BlockPos eastHost = westHost.offset(SHIFT);
        buildReactorShell(helper, eastMin, eastMax);
        int stale = onlyReactorAt(helper, eastMin.offset(0, 2, 2)).id();
        helper.assertTrue(totalMass(helper, eastHost) == 0, "The freshly found chamber should be empty");

        cargo.attach(helper.getLevel(), SHIFT);
        Reactor after = onlyReactorAt(helper, eastMin.offset(0, 2, 2));
        helper.assertTrue(after.id() != stale, "The stale reactor should have made way");
        helper.assertTrue(after.host().equals(helper.absolutePos(eastHost)),
                "The host should have moved with the offset, is " + after.host());
        assertChamber(helper, eastHost, mass, temperature);
        helper.succeed();
    }

    /** Once detached, a lit reactor's blocks can go without it failing: nothing is left to fail. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void detachedLitReactorIsQuietWhenItsBlocksGo(GameTestHelper helper) {
        buildReactorShell(helper, MIN, MAX);
        fillChamber(helper, HOST, ZPSConfig.reactorIgnitionTemperatureK() * 1.5);
        // Let it notice it is lit.
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(onlyReactorAt(helper, WEST_WALL).isLit(), "The reactor should be lit to begin with");
            GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX));

            helper.setBlock(WEST_WALL, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                helper.assertBlockPresent(ModBlocks.REINFORCED_PLATING.get(), EAST_WALL);
                helper.assertTrue(reactorsAt(helper, EAST_WALL).isEmpty(), "No reactor should be left");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void reactorPartlyOutsideTheRegionStays(GameTestHelper helper) {
        buildReactorShell(helper, MIN, MAX);
        int id = onlyReactorAt(helper, WEST_WALL).id();

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX.below()));
        helper.assertTrue(cargo.isEmpty(), "A reactor reaching out of the region should not be taken");
        helper.assertTrue(onlyReactorAt(helper, WEST_WALL).id() == id, "It should be left as it was");
        helper.assertTrue(kelvin().getNodeAt(node(helper, HOST)) instanceof ReactorChamberNode,
                "Its chamber should still be simulated");
        helper.succeed();
    }

    // --- rooms --------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void roomLeavesWithItsGasAndComesBack(GameTestHelper helper) {
        buildRoomShell(helper);
        addFlux(helper, 5.0);
        Room before = rooms(helper).roomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(before != null, "Gas into the shell should make a room");
        Map<ResourceLocation, Double> gas = Map.copyOf(before.massKg());
        double temperature = before.temperatureK();

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX));
        helper.assertTrue(cargo.roomCount() == 1, "The room should be taken, took " + cargo.roomCount());
        helper.assertTrue(rooms(helper).knownRoomAt(helper.absolutePos(CENTRE)) == null,
                "The level should no longer list the room");

        cargo.attach(helper.getLevel(), BlockPos.ZERO);
        Room after = rooms(helper).roomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(after != null, "The room should be back");
        helper.assertTrue(after.id() != before.id(), "The arrival should have an id of the level's own");
        helper.assertTrue(after.volume() == 27, "The room should be the same 27 cells, is " + after.volume());
        helper.assertTrue(after.massKg().equals(gas), "The room should hold " + gas + ", holds " + after.massKg());
        helper.assertTrue(Math.abs(after.temperatureK() - temperature) < 1e-9,
                "The room should be at the " + temperature + " K it left at, is " + after.temperatureK());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void roomArrivingOverAStaleOneReplacesIt(GameTestHelper helper) {
        buildRoomShell(helper);
        addFlux(helper, 5.0);
        double flux = rooms(helper).roomAt(helper.absolutePos(CENTRE)).massKg().get(ModGases.FLUX.getResourceLocation());

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX));
        // Sealed again before the cargo arrives, with a different fill.
        addFlux(helper, 1.0);
        Room stale = rooms(helper).roomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(stale != null, "Gas should seal a fresh room meanwhile");

        cargo.attach(helper.getLevel(), BlockPos.ZERO);
        Room after = rooms(helper).roomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(after != null && after.id() != stale.id(), "The stale room should have made way");
        helper.assertTrue(rooms(helper).knownRoomAt(helper.absolutePos(CENTRE)) == after,
                "Only the arrival should hold the air");
        helper.assertTrue(after.massKg().get(ModGases.FLUX.getResourceLocation()) == flux,
                "The arrival should hold its own " + flux + " kg of flux");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void roomPartlyOutsideTheRegionStays(GameTestHelper helper) {
        buildRoomShell(helper);
        addFlux(helper, 5.0);
        Room before = rooms(helper).roomAt(helper.absolutePos(CENTRE));

        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX.below()));
        helper.assertTrue(cargo.isEmpty(), "A room reaching out of the region should not be taken");
        helper.assertTrue(rooms(helper).roomAt(helper.absolutePos(CENTRE)) == before, "It should be left as it was");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cargoAttachesOnce(GameTestHelper helper) {
        GridTransfer.Cargo cargo = GridTransfer.detach(helper.getLevel(), box(helper, MIN, MAX));
        cargo.attach(helper.getLevel(), BlockPos.ZERO);
        try {
            cargo.attach(helper.getLevel(), BlockPos.ZERO);
        } catch (IllegalStateException expected) {
            helper.succeed();
            return;
        }
        helper.fail("Attaching a second time should be refused");
    }
}
