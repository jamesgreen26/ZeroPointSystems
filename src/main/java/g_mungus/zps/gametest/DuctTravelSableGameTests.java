package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Duct travel on a Sable sublevel.
 * <p>
 * Sable keeps a sublevel's blocks in the same level as everything else, in a plot far from where they appear,
 * and the vent a player clicks on a sublevel is handed to the block as its plot position. The duct vehicle stays
 * in that plot, like a seat, and Sable carries its rider out into the world; but everything the vehicle hands to
 * the world itself — the rider's sounds, and above all where it puts the player when they climb back out — has to
 * be brought into the world first, or the player is sent to the plot, or to wherever a plot position turns into
 * once Sable has finished with it.
 * <p>
 * The sublevel is left unturned, so its blocks appear exactly where they were lifted from and every expected
 * position is simply where the block used to be. Sable is only on the classpath for local runs; where it is
 * missing the tests pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DuctTravelSableGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final String BATCH = "ductTravelSable";

    /** A run along z at head height above the arena floor, a vent at each end facing away from the ducts. */
    private static final int RUN_X = 5;
    private static final int RUN_Y = 3;
    private static final int NEAR_Z = 3;
    private static final int FAR_Z = 7;

    /** How far off the vent a player may be while still counting as at it. */
    private static final double SLACK = 1.5;
    /** Long enough for the hop to be made and the crawl to finish. */
    private static final int HOP_TICKS = DuctTravelEntity.FADE_OUT_TICKS + DuctTravelEntity.MAX_TRAVEL_TICKS / 4;

    /** The run once it is on a sublevel: where its vents are in the plot, and where each appears in the world. */
    private record Run(SableBeamRig rig, BlockPos nearPlot, BlockPos farPlot, Vec3 nearWorld, Vec3 farWorld) {
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_climbingIntoASublevelVentLeavesThePlayerInTheWorld", skyAccess = true)
    public static void climbingIntoASublevelVentLeavesThePlayerInTheWorld(GameTestHelper helper) {
        withRun(helper, run -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            enter(helper, run.nearPlot(), player);

            helper.runAfterDelay(2, () -> {
                assertNear(helper, player.position(), run.nearWorld(),
                        "climbing into a vent on a sublevel should leave the player at that vent in the world");
                assertInThePlot(helper, player.getVehicle().position(), "the duct vehicle after climbing in");
                run.rig().remove();
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_hoppingBetweenSublevelVentsKeepsThePlayerInTheWorld", skyAccess = true)
    public static void hoppingBetweenSublevelVentsKeepsThePlayerInTheWorld(GameTestHelper helper) {
        withRun(helper, run -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            DuctTravelEntity duct = enter(helper, run.nearPlot(), player);
            duct.requestCycle(1);

            helper.runAfterDelay(HOP_TICKS, () -> {
                helper.assertFalse(duct.isTravelling(), "The crawl should be over by now");
                assertInThePlot(helper, duct.position(), "the duct vehicle after a hop");
                assertNear(helper, player.position(), run.farWorld(),
                        "hopping to a vent on a sublevel should leave the player at that vent in the world");
                run.rig().remove();
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_climbingOutOfASublevelVentLandsInTheWorld", skyAccess = true)
    public static void climbingOutOfASublevelVentLandsInTheWorld(GameTestHelper helper) {
        withRun(helper, run -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            DuctTravelEntity duct = enter(helper, run.nearPlot(), player);
            duct.requestCycle(1);

            helper.runAfterDelay(HOP_TICKS, () -> {
                // The far vent faces south, so the player should be stood just south of it, at its height.
                Vec3 outside = run.farWorld().add(0.0, -0.5, 1.0);
                // The spot itself, and not only where the player ends up: Sable brings a dismount that
                // lands in a plot it knows back into the world, which would hide the wrong answer here.
                assertNear(helper, duct.getDismountLocationForPassenger(player), outside,
                        "the spot a rider is put down on from a sublevel vent should be in front of it in the world");
                player.stopRiding();
                assertNear(helper, player.position(), outside,
                        "climbing out of a vent on a sublevel should put the player in front of it in the world");
                run.rig().remove();
                helper.succeed();
            });
        });
    }

    /**
     * The vent the player is peeking out of is broken from under them while their run is on a sublevel: the
     * vehicle throws them out, and it has only the vent's position to go on.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_beingThrownOutOfASublevelVentLandsInTheWorld", skyAccess = true)
    public static void beingThrownOutOfASublevelVentLandsInTheWorld(GameTestHelper helper) {
        withRun(helper, run -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            enter(helper, run.nearPlot(), player);

            helper.runAfterDelay(2, () -> {
                helper.getLevel().removeBlock(run.nearPlot(), false);
                helper.runAfterDelay(2, () -> {
                    helper.assertFalse(player.isPassenger(), "A player whose vent was broken should be thrown out");
                    assertNear(helper, player.position(), run.nearWorld(),
                            "being thrown out of a sublevel vent should leave the player where it was in the world");
                    run.rig().remove();
                    helper.succeed();
                });
            });
        });
    }

    /**
     * The whole sublevel is taken away while the player is inside one of its vents. The vent's position now
     * points at an empty plot that belongs to nothing, so it says nothing about where the player is; the
     * vehicle has to fall back on where it is itself.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_removingTheSublevelUnderARiderLeavesThemWhereItWas", skyAccess = true)
    public static void removingTheSublevelUnderARiderLeavesThemWhereItWas(GameTestHelper helper) {
        withRun(helper, run -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            enter(helper, run.nearPlot(), player);

            helper.runAfterDelay(2, () -> {
                run.rig().remove();
                helper.runAfterDelay(2, () -> {
                    helper.assertFalse(player.isPassenger(), "A player whose sublevel was removed should be thrown out");
                    assertNear(helper, player.position(), run.nearWorld(),
                            "losing the sublevel from under a rider should leave them where its vent was in the world");
                    helper.succeed();
                });
            });
        });
    }

    // --- the rig --------------------------------------------------------------------------------------------

    /** Lays the run in the world, lifts it into a sublevel that stays exactly where it is, and hands it over. */
    private static void withRun(GameTestHelper helper, Consumer<Run> test) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();

        List<BlockPos> blocks = new ArrayList<>();
        BlockPos near = new BlockPos(RUN_X, RUN_Y, NEAR_Z);
        BlockPos far = new BlockPos(RUN_X, RUN_Y, FAR_Z);
        helper.setBlock(near, ModBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.NORTH));
        helper.setBlock(far, ModBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.SOUTH));
        for (int z = NEAR_Z; z <= FAR_Z; z++) {
            BlockPos pos = new BlockPos(RUN_X, RUN_Y, z);
            if (z != NEAR_Z && z != FAR_Z) {
                helper.setBlock(pos, ModBlocks.GAS_DUCT.get().defaultBlockState());
            }
            blocks.add(helper.absolutePos(pos));
        }
        Vec3 nearWorld = Vec3.atCenterOf(helper.absolutePos(near));
        Vec3 farWorld = Vec3.atCenterOf(helper.absolutePos(far));

        // Two ticks for the ducts to settle their connections before they are lifted; Sable does not place
        // the blocks again in the plot, so whatever state they have then is the state they keep.
        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, helper.absolutePos(near), helper.absolutePos(far),
                    blocks, new Quaterniond());

            List<BlockPos> vents = rig.blocksLeft().stream()
                    .filter(pos -> level.getBlockState(pos).getBlock() instanceof VentBlock)
                    .sorted(Comparator.comparingInt(BlockPos::getZ))
                    .toList();
            helper.assertTrue(vents.size() == 2, "Expected both vents in the plot, found " + vents.size());

            test.accept(new Run(rig, vents.get(0), vents.get(1), nearWorld, farWorld));
        });
    }

    private static DuctTravelEntity enter(GameTestHelper helper, BlockPos ventPlotPos, Player player) {
        helper.assertTrue(DuctTravelEntity.enter(helper.getLevel(), ventPlotPos, player),
                "Could not climb into the vent at plot " + ventPlotPos.toShortString());
        helper.assertTrue(player.getVehicle() instanceof DuctTravelEntity, "Climbing in left the player riding nothing");
        return (DuctTravelEntity) player.getVehicle();
    }

    private static void assertNear(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
        assertInTheWorld(helper, actual, what);
        helper.assertTrue(actual.distanceTo(expected) <= SLACK,
                what + ": expected within " + SLACK + " of " + expected + ", was " + actual);
    }

    /**
     * The vehicle itself stays in the sublevel's own space, as a seat would: half in and half out is exactly the
     * state that loses riders.
     */
    private static void assertInThePlot(GameTestHelper helper, Vec3 actual, String what) {
        helper.assertTrue(Compat.gridOf(helper.getLevel(), BlockPos.containing(actual)) != null,
                what + " should sit in the sublevel's plot, was at " + actual);
    }

    /** Neither the origin, which is where a lost position tends to end up, nor anywhere in Sable's plots. */
    private static void assertInTheWorld(GameTestHelper helper, Vec3 actual, String what) {
        helper.assertFalse(actual.lengthSqr() < 4.0, what + " ended up at the origin: " + actual);
        helper.assertTrue(Compat.gridOf(helper.getLevel(), BlockPos.containing(actual)) == null,
                what + " is in a sublevel's plot rather than the world: " + actual);
    }
}
