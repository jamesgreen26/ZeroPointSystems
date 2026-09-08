package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.reactor.ReactorGasWallBlock;
import g_mungus.zps.blockentity.reactor.ReactorGasWallBlockEntity;
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
import org.valkyrienskies.kelvin.api.edges.ApertureEdge;

/**
 * Redstone on the Fuel Injector and the Exhaust Port throttles their outer face: unpowered they
 * pass gas like a duct, at full power nothing gets through. Tested away from any reactor, with
 * gas put straight into the stub or the duct outside it, since the outer face is an ordinary
 * negotiated edge and does not care what the inner face is up to.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorWallApertureGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos WALL = new BlockPos(3, 2, 3);
    private static final BlockPos OUTSIDE = WALL.east();
    private static final BlockPos POWER = WALL.north();

    private static final double EPSILON = 1e-9;

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static double totalMass(GameTestHelper helper, BlockPos relative) {
        double total = 0;
        for (double mass : kelvin().getGasMassAt(node(helper, relative)).values()) {
            total += mass;
        }
        return total;
    }

    private static int redstoneLevel(GameTestHelper helper) {
        if (!(helper.getBlockEntity(WALL) instanceof ReactorGasWallBlockEntity wall)) {
            helper.fail("The wall block has no block entity");
            throw new IllegalStateException();
        }
        return wall.getRedstoneLevel();
    }

    private static DuctEdge outerEdge(GameTestHelper helper) {
        DuctEdge edge = kelvin().getEdgeBetween(node(helper, WALL), node(helper, OUTSIDE));
        if (edge == null) {
            helper.fail("No edge between the wall block and the duct outside it");
            throw new IllegalStateException();
        }
        return edge;
    }

    private static double apertureOf(GameTestHelper helper) {
        DuctEdge edge = outerEdge(helper);
        if (!(edge instanceof ApertureEdge aperture)) {
            helper.fail("The outer edge cannot carry an aperture: " + edge.getClass().getSimpleName());
            throw new IllegalStateException();
        }
        return aperture.getAperture();
    }

    /** A wall block facing east with a duct on its outer face. */
    private static void placeWall(GameTestHelper helper, BlockState wall) {
        helper.setBlock(WALL, wall.setValue(ReactorGasWallBlock.FACING, Direction.EAST));
        helper.setBlock(OUTSIDE, ModBlocks.GAS_DUCT.get().defaultBlockState());
    }

    // --- stored level and aperture ------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void redstoneLevelIsStoredAndSetsTheAperture(GameTestHelper helper) {
        placeWall(helper, ModBlocks.EXHAUST_PORT.get().defaultBlockState());
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(redstoneLevel(helper) == 15,
                    "A redstone block should read as 15, was " + redstoneLevel(helper));
            helper.assertTrue(Math.abs(apertureOf(helper) + ReactorGasWallBlock.RADIUS) < EPSILON,
                    "Full power should close the outer face entirely, aperture was " + apertureOf(helper));

            helper.setBlock(POWER, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(redstoneLevel(helper) == 0,
                    "Removing the signal should drop the level to 0, was " + redstoneLevel(helper));
            helper.assertTrue(Math.abs(apertureOf(helper)) < EPSILON,
                    "Unpowered, the outer face should be fully open, aperture was " + apertureOf(helper));
            helper.succeed();
        });
    }

    // --- flow ---------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void poweredExhaustPortPassesNothing(GameTestHelper helper) {
        placeWall(helper, ModBlocks.EXHAUST_PORT.get().defaultBlockState());
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, WALL), ModGases.AETHER, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, OUTSIDE) < EPSILON,
                    "A fully powered exhaust port must pass nothing, the duct got " + totalMass(helper, OUTSIDE));
            // Open the valve: the same gas should now leave the stub.
            helper.setBlock(POWER, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(totalMass(helper, OUTSIDE) > EPSILON,
                    "Unpowered, the exhaust port should let its stub drain into the duct");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unpoweredExhaustPortPassesGas(GameTestHelper helper) {
        placeWall(helper, ModBlocks.EXHAUST_PORT.get().defaultBlockState());

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, WALL), ModGases.AETHER, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, OUTSIDE) > EPSILON,
                    "An unpowered exhaust port should pass gas out like a duct");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void poweredFuelInjectorAdmitsNothing(GameTestHelper helper) {
        placeWall(helper, ModBlocks.FUEL_INJECTOR.get().defaultBlockState());
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, OUTSIDE), ModGases.FLUX, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, WALL) < EPSILON,
                    "A fully powered injector must admit nothing, the stub got " + totalMass(helper, WALL));
            helper.setBlock(POWER, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(totalMass(helper, WALL) > EPSILON,
                    "Unpowered, the injector should admit gas from the duct");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unpoweredFuelInjectorAdmitsGas(GameTestHelper helper) {
        placeWall(helper, ModBlocks.FUEL_INJECTOR.get().defaultBlockState());

        helper.runAfterDelay(5, () -> kelvin().addGasAtTemperature(node(helper, OUTSIDE), ModGases.FLUX, 0.5, 400.0));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(totalMass(helper, WALL) > EPSILON,
                    "An unpowered injector should admit gas like a duct");
            helper.succeed();
        });
    }
}
