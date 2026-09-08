package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.reactor.ReactorPortBlock;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.edges.ApertureEdge;
import org.valkyrienskies.kelvin.api.edges.OneWayEdge;

import java.util.Set;

/**
 * The Reactor Port's mode and redstone throttle act between its stub and the chamber, never on
 * its outer face. Redstone narrows the input's valve into the chamber and slows the output's
 * pump out of it, to nothing at full power; the outer face stays a plain duct joint throughout.
 *
 * <p>Most tests build a 5x5x5 shell around a 3x3x3 cavity with the port in the middle of the
 * west wall, power it from the block outside, and put gas straight into the stub or the chamber.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorWallApertureGameTests {

    private static final String REACTOR_TEMPLATE = "gametest/flat_11x9x11";
    private static final String FLAT_TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);
    /** Lowest interior cell: where the chamber node lives. */
    private static final BlockPos HOST = MIN.offset(1, 1, 1);
    /** Middle of the west wall, and the block just outside it, where the power goes. */
    private static final BlockPos WALL = new BlockPos(3, 3, 5);
    private static final BlockPos OUTSIDE = WALL.west();

    /** For the outer-face test, which needs no reactor: a port with a duct on its outer face. */
    private static final BlockPos LONE_WALL = new BlockPos(3, 2, 3);
    private static final BlockPos LONE_OUTSIDE = LONE_WALL.east();

    private static final double EPSILON = 1e-9;

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
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

    private static ReactorPortBlockEntity port(GameTestHelper helper) {
        if (!(helper.getBlockEntity(WALL) instanceof ReactorPortBlockEntity port)) {
            helper.fail("The wall block has no block entity");
            throw new IllegalStateException();
        }
        return port;
    }

    private static DuctEdge chamberEdge(GameTestHelper helper) {
        DuctEdge edge = kelvin().getEdgeBetween(node(helper, WALL), node(helper, HOST));
        if (edge == null) {
            helper.fail("No edge between the port's stub and the chamber");
            throw new IllegalStateException();
        }
        return edge;
    }

    private static double chamberApertureOf(GameTestHelper helper) {
        DuctEdge edge = chamberEdge(helper);
        if (!(edge instanceof ApertureEdge aperture)) {
            helper.fail("The chamber edge cannot carry an aperture: " + edge.getClass().getSimpleName());
            throw new IllegalStateException();
        }
        return aperture.getAperture();
    }

    private static BlockState port(ReactorPortMode mode, Direction facing) {
        return ModBlocks.REACTOR_PORT.get().defaultBlockState()
                .setValue(ReactorPortBlock.MODE, mode)
                .setValue(ReactorPortBlock.FACING, facing);
    }

    /** A sealed shell with the port in the middle of the west wall, facing out. */
    private static void buildShell(GameTestHelper helper, ReactorPortMode mode) {
        BlockState plating = ModBlocks.REINFORCED_PLATING.get().defaultBlockState();
        // The port goes in first so the reactor forms once, complete.
        helper.setBlock(WALL, port(mode, Direction.WEST));
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean edge = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            if (edge && !pos.equals(WALL)) {
                helper.setBlock(pos, plating);
            }
        }
    }

    private static void power(GameTestHelper helper, boolean on) {
        helper.setBlock(OUTSIDE, on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    // --- stored level and aperture ------------------------------------------------------------

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void redstoneLevelIsStoredAndSetsTheChamberAperture(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.INPUT);
        power(helper, true);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(port(helper).getRedstoneLevel() == 15,
                    "A redstone block should read as 15, was " + port(helper).getRedstoneLevel());
            helper.assertTrue(Math.abs(chamberApertureOf(helper) + ReactorPortBlockEntity.CHAMBER_EDGE_RADIUS) < EPSILON,
                    "Full power should close the chamber side entirely, aperture was " + chamberApertureOf(helper));

            power(helper, false);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(port(helper).getRedstoneLevel() == 0,
                    "Removing the signal should drop the level to 0, was " + port(helper).getRedstoneLevel());
            helper.assertTrue(Math.abs(chamberApertureOf(helper)) < EPSILON,
                    "Unpowered, the chamber side should be fully open, aperture was " + chamberApertureOf(helper));
            helper.succeed();
        });
    }

    // --- input -----------------------------------------------------------------------------

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void poweredInputAdmitsNothing(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.INPUT);
        power(helper, true);

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, WALL), ModGases.FLUX, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, HOST) < EPSILON,
                    "A fully powered input must admit nothing, the chamber got " + totalMass(helper, HOST));
            // Open the valve: the same gas should now reach the chamber.
            power(helper, false);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(totalMass(helper, HOST) > EPSILON,
                    "Unpowered, the input should let its stub empty into the chamber");
            helper.succeed();
        });
    }

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void unpoweredInputAdmitsGas(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.INPUT);

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, WALL), ModGases.FLUX, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, HOST) > EPSILON,
                    "An unpowered input should pass gas into the chamber");
            helper.succeed();
        });
    }

    // --- output ------------------------------------------------------------------------------

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void poweredOutputDrawsNothing(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.OUTPUT);
        power(helper, true);

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, WALL) < EPSILON,
                    "A fully powered output must draw nothing, the stub got " + totalMass(helper, WALL));
            helper.assertTrue(kelvin().getEdgeBetween(node(helper, WALL), node(helper, HOST)) == null,
                    "An output has no edge to the chamber");
            power(helper, false);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(totalMass(helper, WALL) > EPSILON,
                    "Unpowered, the output should draw the chamber into its stub");
            helper.succeed();
        });
    }

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void unpoweredOutputDrawsGas(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.OUTPUT);

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, WALL) > EPSILON,
                    "An unpowered output should draw gas out of the chamber");
            helper.succeed();
        });
    }

    // --- mode ---------------------------------------------------------------------------------

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 150)
    public static void switchingModeSwapsValveForPump(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.INPUT);

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(chamberEdge(helper) instanceof OneWayEdge, "An input holds a check valve into the chamber");
            helper.assertTrue(totalMass(helper, WALL) < EPSILON,
                    "An input must never let the chamber into its stub, the stub got " + totalMass(helper, WALL));
            port(helper).setMode(ReactorPortMode.OUTPUT);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(ReactorPortBlock.mode(helper.getBlockState(WALL)) == ReactorPortMode.OUTPUT,
                    "The block state should carry the new mode");
            helper.assertTrue(kelvin().getEdgeBetween(node(helper, WALL), node(helper, HOST)) == null,
                    "The input's valve should be gone");
            helper.assertTrue(totalMass(helper, WALL) > EPSILON,
                    "Switched to output, the port should pump the chamber into its stub");
            helper.succeed();
        });
    }

    // --- outer face ---------------------------------------------------------------------------

    @GameTest(template = FLAT_TEMPLATE, timeoutTicks = 100)
    public static void outerFaceIsAPlainDuctJointInEitherMode(GameTestHelper helper) {
        helper.setBlock(LONE_WALL, port(ReactorPortMode.INPUT, Direction.EAST));
        helper.setBlock(LONE_OUTSIDE, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(LONE_WALL.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        // Powered and in input mode, which used to be the most closed the outer face could be.
        helper.runAfterDelay(5, () -> {
            DuctEdge edge = kelvin().getEdgeBetween(node(helper, LONE_WALL), node(helper, LONE_OUTSIDE));
            helper.assertTrue(edge != null, "The outer face should be joined to the duct");
            helper.assertFalse(edge instanceof OneWayEdge, "The outer face carries no check valve");
            helper.assertTrue(!(edge instanceof ApertureEdge aperture) || Math.abs(aperture.getAperture()) < EPSILON,
                    "Redstone must not narrow the outer face");
            kelvin().addGasAtTemperature(node(helper, LONE_WALL), ModGases.AETHER, 0.5, 400.0);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, LONE_OUTSIDE) > EPSILON,
                    "Gas in the stub should drain out to the duct like any pipe joint");
            helper.succeed();
        });
    }

    // --- filter -------------------------------------------------------------------------------

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void inputWhitelistAdmitsOnlyListedGases(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.INPUT);
        port(helper).setSettings(ReactorPortMode.INPUT, new GasFilter(false, Set.of(ModGases.FLUX.getResourceLocation())));

        helper.runAfterDelay(5, () -> {
            kelvin().addGasAtTemperature(node(helper, WALL), ModGases.FLUX, 0.25, 400.0);
            kelvin().addGasAtTemperature(node(helper, WALL), ModGases.AETHER, 0.25, 400.0);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(massOf(helper, HOST, ModGases.FLUX) > EPSILON,
                    "The whitelisted gas should reach the chamber");
            helper.assertTrue(massOf(helper, HOST, ModGases.AETHER) < EPSILON,
                    "A gas off the whitelist must stay out, the chamber got " + massOf(helper, HOST, ModGases.AETHER));
            helper.succeed();
        });
    }

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void outputBlacklistHoldsBackListedGases(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.OUTPUT);
        port(helper).setSettings(ReactorPortMode.OUTPUT, new GasFilter(true, Set.of(ModGases.FLUX.getResourceLocation())));

        helper.runAfterDelay(5, () -> {
            kelvin().addGasAtTemperature(node(helper, HOST), ModGases.FLUX, 0.25, 400.0);
            kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 0.25, 400.0);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(massOf(helper, WALL, ModGases.AETHER) > EPSILON,
                    "A gas off the blacklist should be pumped out");
            helper.assertTrue(massOf(helper, WALL, ModGases.FLUX) < EPSILON,
                    "A blacklisted gas must stay in, the stub got " + massOf(helper, WALL, ModGases.FLUX));
            helper.succeed();
        });
    }

    @GameTest(template = REACTOR_TEMPLATE, timeoutTicks = 100)
    public static void unfilteredOutputPumpsEverything(GameTestHelper helper) {
        buildShell(helper, ReactorPortMode.OUTPUT);

        helper.runAfterDelay(5, () -> {
            kelvin().addGasAtTemperature(node(helper, HOST), ModGases.FLUX, 0.25, 400.0);
            kelvin().addGasAtTemperature(node(helper, HOST), ModGases.AETHER, 0.25, 400.0);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(massOf(helper, WALL, ModGases.FLUX) > EPSILON,
                    "With no filter, the output should pump fuel too");
            helper.assertTrue(massOf(helper, WALL, ModGases.AETHER) > EPSILON,
                    "With no filter, the output should pump ash");
            helper.succeed();
        });
    }
}
