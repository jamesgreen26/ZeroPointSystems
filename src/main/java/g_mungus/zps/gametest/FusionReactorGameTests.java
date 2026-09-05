package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.reactor.HeatExchangerBlock;
import g_mungus.zps.block.reactor.ReactorGasWallBlock;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.blockentity.reactor.HeatExchangerBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorFailures;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fusion reactor as a whole: a sealed shell becomes a reactor with one chamber node, the
 * functional wall blocks move gas and energy the way they should, and the failures take it apart.
 *
 * <p>Every test builds a 5x5x5 shell around a 3x3x3 cavity, corner {@link #MIN} to {@link #MAX}.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class FusionReactorGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";

    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);
    /** Lowest interior cell: where the chamber node lives. */
    private static final BlockPos HOST = MIN.offset(1, 1, 1);
    /** Middle of the west wall, and the block just outside it. */
    private static final BlockPos WEST_WALL = new BlockPos(3, 3, 5);
    private static final BlockPos OUTSIDE_WEST = WEST_WALL.west();
    /** Middle of the east wall, and the block just outside it. */
    private static final BlockPos EAST_WALL = new BlockPos(7, 3, 5);
    private static final BlockPos OUTSIDE_EAST = EAST_WALL.east();
    /** Two spots on the north wall, and the blocks just outside them. */
    private static final BlockPos NORTH_WALL_A = new BlockPos(5, 3, 3);
    private static final BlockPos NORTH_WALL_B = new BlockPos(4, 3, 3);
    private static final BlockPos OUTSIDE_NORTH_A = NORTH_WALL_A.north();
    private static final BlockPos OUTSIDE_NORTH_B = NORTH_WALL_B.north();

    private static final int SMALL_WALLS = 54;
    private static final double EPSILON = 1e-9;

    // --- helpers ------------------------------------------------------------------------------

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static ReactorManager manager(GameTestHelper helper) {
        return ReactorManager.get(helper.getLevel());
    }

    private static Reactor reactorAt(GameTestHelper helper, BlockPos relative) {
        List<Reactor> reactors = manager(helper).reactorsAt(helper.absolutePos(relative));
        if (reactors.size() != 1) {
            helper.fail("Expected exactly one reactor at " + relative + ", found " + reactors.size());
            throw new IllegalStateException();
        }
        return reactors.get(0);
    }

    private static double massOf(GameTestHelper helper, BlockPos relative, GasType gas) {
        return kelvin().getGasMassAt(node(helper, relative)).getOrDefault(gas, 0.0);
    }

    private static double totalMass(GameTestHelper helper, BlockPos relative) {
        double total = 0;
        for (double mass : kelvin().getGasMassAt(node(helper, relative)).values()) {
            total += mass;
        }
        return total;
    }

    /** Put the chamber at a temperature by writing its energy; gas already there stays. */
    private static void setChamberTemperature(GameTestHelper helper, double temperature) {
        DuctNodePos host = node(helper, HOST);
        double current = kelvin().getTemperatureAt(host);
        kelvin().modHeatEnergy(host, (temperature - current) * kelvin().getNodeHeatCapacity(host));
    }

    private static BlockState facing(BlockState state, Direction direction) {
        return state.setValue(ReactorGasWallBlock.FACING, direction);
    }

    /**
     * Build the shell, plating everywhere except where {@code overrides} says otherwise. The
     * overrides go in first so the reactor forms once, complete.
     */
    private static void buildShell(GameTestHelper helper, Map<BlockPos, BlockState> overrides) {
        BlockState plating = ModBlocks.REINFORCED_PLATING.get().defaultBlockState();
        for (Map.Entry<BlockPos, BlockState> override : overrides.entrySet()) {
            helper.setBlock(override.getKey(), override.getValue());
        }
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean edge = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            if (edge && !overrides.containsKey(pos)) {
                helper.setBlock(pos, plating);
            }
        }
    }

    private static void buildShell(GameTestHelper helper) {
        buildShell(helper, Map.of());
    }

    private static CreativeGasGeneratorBlockEntity placeGenerator(GameTestHelper helper, BlockPos relative,
                                                                  double rate, double temperature) {
        helper.setBlock(relative, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(relative);
        if (!(blockEntity instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("The gas source has no block entity");
            throw new IllegalStateException();
        }
        generator.setSettings(ModGases.FLUX.getResourceLocation(), rate, temperature);
        return generator;
    }

    // --- detection ----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void sealingRegistersReactor(GameTestHelper helper) {
        buildShell(helper);

        Reactor reactor = reactorAt(helper, WEST_WALL);
        helper.assertTrue(reactor.host().equals(helper.absolutePos(HOST)),
                "Host should be the lowest interior cell, was " + reactor.host());
        helper.assertTrue(reactor.volume() == 27, "Volume should be 27, was " + reactor.volume());
        helper.assertTrue(reactor.wallCount() == SMALL_WALLS, "Wall count should be 54, was " + reactor.wallCount());
        helper.assertTrue(Math.abs(reactor.compactness() - 1.0) < EPSILON, "A cube is fully compact");
        helper.assertTrue(kelvin().getNodeAt(node(helper, HOST)) instanceof ReactorChamberNode,
                "The chamber node should exist at the host");
        helper.assertTrue(manager(helper).reactorForInterior(helper.absolutePos(HOST.above())) == reactor,
                "Interior cells should map to the reactor");
        helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(MIN)).isEmpty(),
                "A corner block has no face on the cavity and is not part of the reactor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void breakingAWallDissolves(GameTestHelper helper) {
        buildShell(helper);
        reactorAt(helper, WEST_WALL);

        helper.setBlock(WEST_WALL, Blocks.AIR.defaultBlockState());

        helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(EAST_WALL)).isEmpty(),
                "The reactor should be gone once the shell leaks");
        helper.assertTrue(kelvin().getNodeAt(node(helper, HOST)) == null,
                "The chamber node should be gone with it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void resealingRegistersAgain(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL, Blocks.AIR.defaultBlockState());
        helper.setBlock(WEST_WALL, ModBlocks.REINFORCED_PLATING.get().defaultBlockState());

        Reactor reactor = reactorAt(helper, WEST_WALL);
        helper.assertTrue(reactor.wallCount() == SMALL_WALLS, "The rebuilt reactor should be whole");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void extraOuterLayerIsIgnored(GameTestHelper helper) {
        buildShell(helper);
        Reactor before = reactorAt(helper, WEST_WALL);

        // A slab of plating against the whole west face, one block out.
        for (int y = MIN.getY(); y <= MAX.getY(); y++) {
            for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                helper.setBlock(new BlockPos(MIN.getX() - 1, y, z), ModBlocks.REINFORCED_PLATING.get().defaultBlockState());
            }
        }

        Reactor after = reactorAt(helper, WEST_WALL);
        helper.assertTrue(after == before, "The reactor should be untouched by blocks outside its wall");
        helper.assertTrue(after.wallCount() == SMALL_WALLS, "Outer blocks do not count as wall");
        helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(OUTSIDE_WEST)).isEmpty(),
                "An outer block is not part of the reactor");
        helper.succeed();
    }

    // --- fuel injector ------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void injectorLetsFuelIn(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.FUEL_INJECTOR.get().defaultBlockState(), Direction.WEST)));
        placeGenerator(helper, OUTSIDE_WEST, 0.001, 300.0);

        helper.succeedWhen(() -> helper.assertTrue(massOf(helper, HOST, ModGases.FLUX) > 0,
                "Flux should reach the chamber through the injector"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void injectorNeverLetsGasOut(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.FUEL_INJECTOR.get().defaultBlockState(), Direction.WEST)));
        // A generator at rest, so the injector's outer face is joined to something.
        placeGenerator(helper, OUTSIDE_WEST, 0.0, 300.0);
        kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 1.0, 300.0);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(totalMass(helper, WEST_WALL) < EPSILON,
                    "Nothing should flow back out through the injector, found " + totalMass(helper, WEST_WALL));
            helper.assertTrue(totalMass(helper, OUTSIDE_WEST) < EPSILON,
                    "Nothing should reach the supply line");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void misorientedInjectorIsInert(GameTestHelper helper) {
        // Facing along the wall rather than out of it: still seals, but serves no reactor.
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.FUEL_INJECTOR.get().defaultBlockState(), Direction.NORTH)));

        Reactor reactor = reactorAt(helper, WEST_WALL);
        helper.assertTrue(reactor.wallCount() == SMALL_WALLS, "The shell should still seal");
        helper.assertTrue(manager(helper).reactorServedBy(helper.absolutePos(WEST_WALL), Direction.NORTH) == null,
                "A block facing along the wall serves nothing");
        helper.succeed();
    }

    // --- exhaust port -------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void exhaustDrawsAetherNotFluxAndCools(GameTestHelper helper) {
        buildShell(helper, Map.of(EAST_WALL,
                facing(ModBlocks.EXHAUST_PORT.get().defaultBlockState(), Direction.EAST)));
        DuctNodePos host = node(helper, HOST);
        // Equal parts fuel and ash: the reaction is inhibited, so the flux stays put.
        kelvin().addGasAtTemperature(host, ModGases.FLUX, 0.05, 300.0);
        kelvin().addGasAtTemperature(host, ModGases.AETHER, 0.05, 300.0);
        setChamberTemperature(helper, 60_000.0);

        // Only a few ticks: once enough ash is drawn off the reaction resumes and eats the flux,
        // which is the point of the exhaust but not what this test is checking.
        helper.runAfterDelay(4, () -> {
            double drawn = massOf(helper, EAST_WALL, ModGases.AETHER);
            helper.assertTrue(drawn > 0, "Aether should have reached the exhaust stub");
            helper.assertTrue(massOf(helper, EAST_WALL, ModGases.FLUX) < EPSILON,
                    "Flux must never leave through the exhaust");
            helper.assertTrue(Math.abs(massOf(helper, HOST, ModGases.FLUX) - 0.05) < 1e-6,
                    "The chamber's flux should be untouched");
            helper.assertTrue(massOf(helper, HOST, ModGases.AETHER) < 0.05,
                    "The chamber's aether should be going down");
            double temperature = kelvin().getTemperatureAt(node(helper, EAST_WALL));
            helper.assertTrue(temperature <= ZPSConfig.exhaustOutletTemperatureK() + 1.0,
                    "The stub should be cooled to the outlet temperature, was " + temperature);
            helper.succeed();
        });
    }

    // --- heat exchanger -----------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void exchangerHeatsFromCreativeCell(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.WEST)));
        helper.setBlock(OUTSIDE_WEST, ModBlocks.CREATIVE_POWER_CELL.get().defaultBlockState());

        helper.runAfterDelay(20, () -> helper.assertTrue(
                kelvin().getTemperatureAt(node(helper, HOST)) > 1000.0, "The chamber should be heating"));
        helper.runAfterDelay(300, () -> {
            double temperature = kelvin().getTemperatureAt(node(helper, HOST));
            double cutoff = ZPSConfig.exchangerHeatingCutoffK();
            helper.assertTrue(temperature >= cutoff - 1000.0 && temperature <= cutoff + 3000.0,
                    "Heating should stop at the cutoff, was " + temperature);
            helper.assertTrue(reactorAt(helper, WEST_WALL).hasIgnited(), "The reactor should have ignited");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void exchangerGeneratesIntoPowerCell(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.WEST)));
        helper.setBlock(OUTSIDE_WEST, ModBlocks.POWER_CELL.get().defaultBlockState());
        setChamberTemperature(helper, 90_000.0);

        int ticks = 20;
        helper.runAfterDelay(ticks, () -> {
            BlockEntity cell = helper.getBlockEntity(OUTSIDE_WEST);
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(OUTSIDE_WEST), cell.getBlockState(), cell, Direction.EAST);
            helper.assertTrue(energy != null, "The power cell should expose energy");
            int stored = energy.getEnergyStored();
            int perTick = ZPSConfig.exchangerFePerTick();
            helper.assertTrue(stored >= perTick * (ticks - 3) && stored <= perTick * ticks,
                    "Expected about " + perTick + " FE/t into the cell, got " + stored + " over " + ticks + " ticks");
            helper.assertTrue(kelvin().getTemperatureAt(node(helper, HOST)) < 90_000.0,
                    "Generating should cool the chamber");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void exchangerStopsAtTheFloor(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.WEST)));
        helper.setBlock(OUTSIDE_WEST, ModBlocks.POWER_CELL.get().defaultBlockState());
        double floor = ZPSConfig.exchangerGenerationFloorK();
        setChamberTemperature(helper, floor + 500.0);

        helper.runAfterDelay(60, () -> {
            double temperature = kelvin().getTemperatureAt(node(helper, HOST));
            helper.assertTrue(temperature >= floor - 1.0 && temperature <= floor + 1.0,
                    "The exchanger should stop at the floor, chamber was " + temperature);
            helper.succeed();
        });
    }

    // --- reaction -----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fluxFusesInAHotChamber(GameTestHelper helper) {
        buildShell(helper);
        DuctNodePos host = node(helper, HOST);
        kelvin().addGasAtTemperature(host, ModGases.FLUX, 0.01, 300.0);
        setChamberTemperature(helper, 60_000.0);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(massOf(helper, HOST, ModGases.FLUX) < 1e-6, "The flux should have fused");
            helper.assertTrue(massOf(helper, HOST, ModGases.AETHER) > 0.009, "Aether should have formed");
            helper.assertTrue(kelvin().getTemperatureAt(host) > 61_000.0,
                    "Fusion should heat the chamber, was " + kelvin().getTemperatureAt(host));
            helper.succeed();
        });
    }

    // --- failures -----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void meltBreachRemovesOneWall(GameTestHelper helper) {
        buildShell(helper);
        Reactor reactor = reactorAt(helper, WEST_WALL);
        setChamberTemperature(helper, ZPSConfig.reactorMeltTemperatureK() + 50_000.0);

        helper.runAfterDelay(5, () -> {
            int missing = 0;
            for (long wall : reactor.walls()) {
                if (!helper.getLevel().getBlockState(BlockPos.of(wall)).is(ModBlocks.REINFORCED_PLATING.get())) {
                    missing++;
                }
            }
            helper.assertTrue(missing == 1, "Exactly one wall block should give way, " + missing + " did");
            helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(EAST_WALL)).isEmpty(),
                    "The reactor should be gone");
            helper.assertTrue(kelvin().getNodeAt(node(helper, HOST)) == null, "The chamber node should be gone");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void breakingAHotWallBreaches(GameTestHelper helper) {
        buildShell(helper);
        setChamberTemperature(helper, ZPSConfig.reactorIgnitionTemperatureK() + 5_000.0);

        // Let the reactor tick once so it knows it is lit, then open it.
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(reactorAt(helper, WEST_WALL).isLit(), "The reactor should register as lit");
            helper.setBlock(WEST_WALL, Blocks.AIR.defaultBlockState());
            helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(EAST_WALL)).isEmpty(),
                    "The reactor should be gone");
            helper.succeed();
        });
    }

    // --- persistence --------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void chamberSurvivesSaveAndLoad(GameTestHelper helper) {
        buildShell(helper);
        ServerLevel level = helper.getLevel();
        DuctNodePos host = node(helper, HOST);
        kelvin().addGasAtTemperature(host, ModGases.AETHER, 0.5, 300.0);
        setChamberTemperature(helper, 70_000.0);

        // Kelvin re-derives temperature from energy on its own tick; save after it has.
        helper.runAfterDelay(2, () -> {
            double energyBefore = kelvin().getHeatEnergy(host);
            double temperatureBefore = kelvin().getTemperatureAt(host);
            double massBefore = massOf(helper, HOST, ModGases.AETHER);
            helper.assertTrue(temperatureBefore > 60_000.0, "The chamber should be hot before saving");

            CompoundTag saved = manager(helper).save(new CompoundTag(), level.registryAccess());
            // Simulate the reload: the node is gone, a fresh manager comes back from disk.
            kelvin().removeNode(host);
            ReactorManager loaded = ReactorManager.load(saved, level.registryAccess());
            Reactor reactor = loaded.reactorsAt(helper.absolutePos(WEST_WALL)).get(0);
            loaded.ensureNode(level, reactor);

            helper.assertTrue(kelvin().getNodeAt(host) instanceof ReactorChamberNode, "The node should be back");
            helper.assertTrue(Math.abs(massOf(helper, HOST, ModGases.AETHER) - massBefore) < 1e-9, "The gas should be back");
            helper.assertTrue(Math.abs(kelvin().getHeatEnergy(host) - energyBefore) < 1.0, "The energy should be back");
            helper.assertTrue(Math.abs(kelvin().getTemperatureAt(host) - temperatureBefore) < 1.0,
                    "The temperature should be back, was " + kelvin().getTemperatureAt(host));
            helper.assertTrue(reactor.wallCount() == SMALL_WALLS, "Geometry should be back");
            helper.succeed();
        });
    }

    // --- reported bugs, kept failing until fixed ------------------------------------------------

    private static IEnergyStorage energyAt(GameTestHelper helper, BlockPos relative, Direction side) {
        BlockEntity blockEntity = helper.getBlockEntity(relative);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(relative), blockEntity.getBlockState(), blockEntity, side);
        if (energy == null) {
            helper.fail("No energy storage at " + relative + " on side " + side);
            throw new IllegalStateException();
        }
        return energy;
    }

    private static double chamberTemperature(GameTestHelper helper) {
        return kelvin().getTemperatureAt(node(helper, HOST));
    }

    /**
     * Bug: after melting through the shell and patching the hole, a heater exchanger on a
     * Creative Power Cell never heats the rebuilt chamber until the block is replaced.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void heaterRecoversAfterMeltAndPatch(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.WEST)));
        helper.setBlock(OUTSIDE_WEST, ModBlocks.CREATIVE_POWER_CELL.get().defaultBlockState());

        helper.runAfterDelay(160, () -> {
            helper.assertTrue(chamberTemperature(helper) >= ZPSConfig.exchangerHeatingCutoffK() - 1000.0,
                    "The heater should have brought the chamber to the cutoff first, was " + chamberTemperature(helper));
            // Then it runs hard for a while, well past the cutoff, before it gets away from us.
            setChamberTemperature(helper, 90_000.0);
        });
        helper.runAfterDelay(200, () -> {
            Reactor reactor = reactorAt(helper, WEST_WALL);

            // Melt through a plain wall on the far side, then patch it.
            ReactorFailures.breach(helper.getLevel(), reactor, helper.absolutePos(EAST_WALL), true);
            helper.assertTrue(manager(helper).reactorsAt(helper.absolutePos(WEST_WALL)).isEmpty(),
                    "The breach should have taken the reactor apart");
            helper.assertTrue(!helper.getBlockState(EAST_WALL).is(ModBlocks.REINFORCED_PLATING.get()),
                    "The breached wall should be gone");
            helper.setBlock(EAST_WALL, ModBlocks.REINFORCED_PLATING.get().defaultBlockState());

            reactorAt(helper, WEST_WALL);
            helper.assertTrue(chamberTemperature(helper) < 1000.0,
                    "The patched reactor should start cold, was " + chamberTemperature(helper));
        });
        helper.runAfterDelay(340, () -> {
            double temperature = chamberTemperature(helper);
            helper.assertTrue(temperature > 5000.0,
                    "The heater should be heating the patched reactor again, chamber is at " + temperature);
            helper.succeed();
        });
    }

    /**
     * The same failure without the melt: a heater exchanger on a Creative Power Cell sits next
     * to a chamber that runs well past the cutoff, then the chamber cools. It must heat again.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void heaterResumesAfterChamberCools(GameTestHelper helper) {
        buildShell(helper, Map.of(WEST_WALL,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.WEST)));
        helper.setBlock(OUTSIDE_WEST, ModBlocks.CREATIVE_POWER_CELL.get().defaultBlockState());

        // Well past the cutoff, the way a hard-running reactor is; the cell cannot take FE back.
        helper.runAfterDelay(5, () -> setChamberTemperature(helper, 90_000.0));
        helper.runAfterDelay(60, () -> setChamberTemperature(helper, 300.0));
        helper.runAfterDelay(160, () -> {
            double temperature = chamberTemperature(helper);
            helper.assertTrue(temperature > 5000.0,
                    "The heater should be heating the cooled chamber again, chamber is at " + temperature);
            helper.succeed();
        });
    }

    /**
     * Bug: a reactor that is plainly producing power reports itself as not running. Sampled
     * every tick over a steady-state window: it must never claim to be stalled or cold while
     * the exchangers are delivering FE every tick.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void readoutReportsRunningWhileProducing(GameTestHelper helper) {
        BlockState exchangerNorth = facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.NORTH);
        Map<BlockPos, BlockState> overrides = new HashMap<>();
        overrides.put(WEST_WALL, facing(ModBlocks.FUEL_INJECTOR.get().defaultBlockState(), Direction.WEST));
        overrides.put(EAST_WALL, facing(ModBlocks.EXHAUST_PORT.get().defaultBlockState(), Direction.EAST));
        overrides.put(NORTH_WALL_A, exchangerNorth);
        overrides.put(NORTH_WALL_B, exchangerNorth);
        buildShell(helper, overrides);
        // Fuel in, ash out, power out: the whole loop, sized so the exchangers keep up.
        placeGenerator(helper, OUTSIDE_WEST, 0.0008, 300.0);
        helper.setBlock(OUTSIDE_EAST, facing(ModBlocks.VENT.get().defaultBlockState(), Direction.EAST));
        helper.setBlock(OUTSIDE_NORTH_A, ModBlocks.POWER_CELL.get().defaultBlockState());
        helper.setBlock(OUTSIDE_NORTH_B, ModBlocks.POWER_CELL.get().defaultBlockState());
        setChamberTemperature(helper, 60_000.0);

        int windowStart = 120;
        int windowLength = 40;
        int[] runningTicks = {0};
        int[] outputTicks = {0};
        int[] energyAtStart = {0};
        helper.runAtTickTime(windowStart - 1, () ->
                energyAtStart[0] = energyAt(helper, OUTSIDE_NORTH_A, Direction.SOUTH).getEnergyStored());
        for (int tick = windowStart; tick < windowStart + windowLength; tick++) {
            helper.runAtTickTime(tick, () -> {
                Reactor reactor = reactorAt(helper, WEST_WALL);
                if (reactor.isRunning()) {
                    runningTicks[0]++;
                }
                if (reactor.feOutLastTick() > 0) {
                    outputTicks[0]++;
                }
            });
        }
        helper.runAtTickTime(windowStart + windowLength, () -> {
            int gained = energyAt(helper, OUTSIDE_NORTH_A, Direction.SOUTH).getEnergyStored() - energyAtStart[0];
            helper.assertTrue(gained > 0, "The power cell should be charging, so the reactor is plainly working");
            helper.assertTrue(chamberTemperature(helper) >= ZPSConfig.reactorIgnitionTemperatureK(),
                    "The chamber should be above ignition, was " + chamberTemperature(helper));
            helper.assertTrue(outputTicks[0] == windowLength,
                    "FE out should be reported every tick, was on " + outputTicks[0] + " of " + windowLength);
            helper.assertTrue(runningTicks[0] == windowLength,
                    "A producing reactor should report running every tick, did on " + runningTicks[0] + " of " + windowLength);
            helper.succeed();
        });
    }

    /**
     * Bug: FE pulled out of an exchanger by a consumer that extracts, the way a Step-Up
     * Transformer does, is never counted, so the readout and the HUD show zero output.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void readoutCountsEnergyPulledFromExchanger(GameTestHelper helper) {
        buildShell(helper, Map.of(NORTH_WALL_A,
                facing(ModBlocks.HEAT_EXCHANGER.get().defaultBlockState(), Direction.NORTH)));
        setChamberTemperature(helper, 90_000.0);

        int[] pulled = {0};
        for (int tick = 5; tick < 30; tick++) {
            helper.runAtTickTime(tick, () -> pulled[0] += energyAt(helper, NORTH_WALL_A, Direction.NORTH)
                    .extractEnergy(ZPSConfig.exchangerFePerTick(), false));
        }
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(pulled[0] > 0, "Pulling from the exchanger should yield FE");
            Reactor reactor = reactorAt(helper, NORTH_WALL_A);
            HeatExchangerBlockEntity exchanger = (HeatExchangerBlockEntity) helper.getBlockEntity(NORTH_WALL_A);
            helper.assertTrue(reactor.feOutLastTick() > 0,
                    "The reactor should count FE pulled out of its exchanger, reported " + reactor.feOutLastTick());
            helper.assertTrue(exchanger.getInfo() > 0,
                    "The exchanger HUD should show FE pulled out of it, reported " + exchanger.getInfo());
            helper.succeed();
        });
    }
}
