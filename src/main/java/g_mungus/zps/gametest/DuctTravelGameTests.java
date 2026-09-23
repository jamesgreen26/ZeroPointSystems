package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.block.gas.core.DuctTravelNetwork;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Which vents a player can reach from the one they climbed into.
 *
 * <p>The rest of duct travel is a vehicle and a HUD; this is the part with a decision in it, so it
 * is the part worth pinning down.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DuctTravelGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    /**
     * A run along the z axis: vent, three ducts, vent. A vent joins the network on the face behind
     * its grille and nowhere else, so each one faces away from the ducts, capping its end of the run.
     */
    private static final BlockPos NEAR_VENT = new BlockPos(3, 2, 1);
    private static final BlockPos FAR_VENT = new BlockPos(3, 2, 5);
    private static final BlockPos MIDDLE_DUCT = new BlockPos(3, 2, 3);

    private static void placeRun(GameTestHelper helper) {
        helper.setBlock(NEAR_VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.NORTH));
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(3, 2, z), ModBlocks.GAS_DUCT.get().defaultBlockState());
        }
        helper.setBlock(FAR_VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.SOUTH));
    }

    private static List<BlockPos> reachableFromNear(GameTestHelper helper) {
        return DuctTravelNetwork.reachableVents(helper.getLevel(), helper.absolutePos(NEAR_VENT));
    }

    private static boolean contains(GameTestHelper helper, List<BlockPos> vents, BlockPos relative) {
        return vents.contains(helper.absolutePos(relative));
    }

    @GameTest(template = TEMPLATE)
    public static void aRunOfDuctLinksTheVentsAtItsEnds(GameTestHelper helper) {
        placeRun(helper);

        List<BlockPos> vents = reachableFromNear(helper);
        if (vents.size() != 2) {
            helper.fail("A run with a vent at each end should offer two vents, offered " + vents.size());
            return;
        }
        // Index 0 is always the way back in.
        if (!vents.get(0).equals(helper.absolutePos(NEAR_VENT))) {
            helper.fail("The vent climbed into should be first in the list, was " + vents.get(0));
        }
        if (!contains(helper, vents, FAR_VENT)) {
            helper.fail("The vent at the far end of the run should be reachable");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aShutVentIsNotOfferedAsAnExit(GameTestHelper helper) {
        placeRun(helper);
        // A lever on the far vent shuts it to people as well as to gas.
        helper.setBlock(FAR_VENT, helper.getBlockState(FAR_VENT).setValue(VentBlock.POWERED, true));

        List<BlockPos> vents = reachableFromNear(helper);
        if (contains(helper, vents, FAR_VENT)) {
            helper.fail("A shut vent should not be offered as somewhere to climb out");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void breakingTheRunSeparatesTheVents(GameTestHelper helper) {
        placeRun(helper);
        helper.setBlock(MIDDLE_DUCT, Blocks.AIR.defaultBlockState());

        List<BlockPos> vents = reachableFromNear(helper);
        if (contains(helper, vents, FAR_VENT)) {
            helper.fail("A vent on the other side of a gap in the run should be unreachable");
        }
        if (vents.size() != 1) {
            helper.fail("Only the vent climbed into should remain, offered " + vents.size());
        }
        helper.succeed();
    }

    // --- the run changing under a rider ---------------------------------------------------------

    /** Ticks from asking for the next vent to the hop being made, with a little to spare. */
    private static final int HOP_TICKS = DuctTravelEntity.FADE_OUT_TICKS + 4;

    /** Climb into the near vent and ask for the next vent along. */
    private static DuctTravelEntity enterNearAndCycle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(NEAR_VENT), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            throw new GameTestAssertException("Climbing in left the player riding nothing");
        }
        duct.requestCycle(1);
        return duct;
    }

    /** The baseline the next test is measured against: on an intact run, cycling does hop. */
    @GameTest(template = TEMPLATE)
    public static void cyclingHopsToTheFarVent(GameTestHelper helper) {
        placeRun(helper);
        DuctTravelEntity duct = enterNearAndCycle(helper);

        helper.runAfterDelay(HOP_TICKS, () -> {
            if (!duct.getVentPos().equals(helper.absolutePos(FAR_VENT))) {
                helper.fail("Cycling on an intact run should have hopped to the far vent, but the rider is at "
                        + duct.getVentPos());
            }
            helper.succeed();
        });
    }

    /**
     * The list of vents is drawn up on the way in. If the run is cut afterwards, the vents on the
     * far side of the cut are no longer anywhere the player can crawl to, however recently they
     * were.
     */
    @GameTest(template = TEMPLATE)
    public static void breakingTheRunAfterEnteringStopsTravelAcrossTheGap(GameTestHelper helper) {
        placeRun(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(NEAR_VENT), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            throw new GameTestAssertException("Climbing in left the player riding nothing");
        }

        helper.setBlock(MIDDLE_DUCT, Blocks.AIR.defaultBlockState());
        duct.requestCycle(1);

        helper.runAfterDelay(HOP_TICKS, () -> {
            if (!duct.getVentPos().equals(helper.absolutePos(NEAR_VENT))) {
                helper.fail("A vent across a gap in the run should be out of reach, but the rider hopped to "
                        + duct.getVentPos());
            }
            if (!player.isPassenger()) {
                helper.fail("The rider should still be in the vent they climbed into");
            }
            helper.succeed();
        });
    }

    /** Once the run is mended, the vents beyond the old break are back on offer without climbing out. */
    @GameTest(template = TEMPLATE)
    public static void mendingTheRunRestoresTravelAcrossTheGap(GameTestHelper helper) {
        placeRun(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(NEAR_VENT), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            throw new GameTestAssertException("Climbing in left the player riding nothing");
        }

        helper.setBlock(MIDDLE_DUCT, Blocks.AIR.defaultBlockState());
        duct.requestCycle(1);

        // Long enough for the refused hop's cooldown to run out as well.
        helper.runAfterDelay(DuctTravelEntity.FADE_OUT_TICKS + DuctTravelEntity.FADE_IN_TICKS + 4, () -> {
            if (!duct.getVentPos().equals(helper.absolutePos(NEAR_VENT))) {
                helper.fail("The rider should not have crossed the gap, but is at " + duct.getVentPos());
            }
            helper.setBlock(MIDDLE_DUCT, ModBlocks.GAS_DUCT.get().defaultBlockState());
            duct.requestCycle(1);

            helper.runAfterDelay(HOP_TICKS, () -> {
                if (!duct.getVentPos().equals(helper.absolutePos(FAR_VENT))) {
                    helper.fail("With the run mended the far vent should be reachable again, but the rider is at "
                            + duct.getVentPos());
                }
                helper.succeed();
            });
        });
    }

    /**
     * The cooldown after a hop lands is over before the rider's client can possibly ask again.
     *
     * <p>The client begins fading to black the moment the key is pressed and comes back only when
     * the server answers, so a request turned down for landing on a cooldown with a tick left on
     * it left the rider in the dark, bar empty, for as long as the fade took to give up. The client
     * asks no sooner than {@link DuctTravelEntity#FADE_IN_TICKS} after the arrival reaches it, and
     * a request is handled between two server ticks, so the earliest one there can be is handled
     * {@code FADE_IN_TICKS - 1} server ticks after the arrival was sent. That one must be taken —
     * while one a tick after landing is still, rightly, turned down, and says so.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theFirstRequestTheClientCanMakeAfterLandingIsTaken(GameTestHelper helper) {
        placeRun(helper);
        DuctTravelEntity duct = enterNearAndCycle(helper);

        boolean[] setOff = {false};
        int[] sinceArrival = {-1};
        helper.onEachTick(() -> {
            if (sinceArrival[0] < 0) {
                if (duct.isTravelling()) {
                    setOff[0] = true;
                    return;
                }
                if (!setOff[0]) {
                    return;
                }
                // The hop landed in this tick's entity phase, which is when the arrival was sent.
                sinceArrival[0] = 0;
            } else {
                sinceArrival[0]++;
            }

            int since = sinceArrival[0];
            if (since == 1) {
                if (duct.requestCycle(1)) {
                    helper.fail("A request a tick after landing should still be on cooldown");
                }
            } else if (since == DuctTravelEntity.FADE_IN_TICKS - 1) {
                if (!duct.requestCycle(1)) {
                    helper.fail("The first request the client can make after landing, "
                            + since + " ticks on, should be taken rather than refused");
                }
                helper.succeed();
            }
        });
    }

    /**
     * A rider who is going nowhere still finds out the run has changed: the list is checked against
     * it every couple of seconds, not only when a hop is asked for.
     */
    @GameTest(template = TEMPLATE)
    public static void aRiderSittingStillLearnsTheRunWasCut(GameTestHelper helper) {
        placeRun(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(NEAR_VENT), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            throw new GameTestAssertException("Climbing in left the player riding nothing");
        }
        if (duct.getDestinations().size() != 2) {
            helper.fail("The intact run should offer two vents, offered " + duct.getDestinations().size());
        }

        helper.setBlock(MIDDLE_DUCT, Blocks.AIR.defaultBlockState());

        helper.runAfterDelay(DuctTravelEntity.REFRESH_INTERVAL_TICKS + 4, () -> {
            if (duct.getDestinations().size() != 1) {
                helper.fail("With the run cut, only the rider's own vent should be on offer, but "
                        + duct.getDestinations().size() + " are");
            }
            helper.succeed();
        });
    }

    // --- hot gas ----------------------------------------------------------------------------------

    /** The vent a rider sits in for the gas tests, fed from behind by a creative source. */
    private static final BlockPos GAS_VENT = new BlockPos(3, 2, 3);
    private static final BlockPos GAS_SOURCE = new BlockPos(3, 2, 2);
    /**
     * Long enough for the vent's node to warm to the source's temperature. Kelvin heats a node
     * gradually as gas passes through it, so the vent is not hot the moment the source is.
     */
    private static final int WARM_UP_TICKS = 80;

    /**
     * Climb into a vent with a creative source pressed against its inlet, running Flux through it
     * at {@code temperature} kelvin.
     */
    private static Player enterVentFedAt(GameTestHelper helper, double temperature) {
        helper.setBlock(GAS_VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.SOUTH));
        helper.setBlock(GAS_SOURCE, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        if (!(helper.getBlockEntity(GAS_SOURCE) instanceof CreativeGasGeneratorBlockEntity source)) {
            throw new GameTestAssertException("The gas source has no block entity");
        }
        source.setSettings(ModGases.FLUX.getResourceLocation(), 0.01, temperature);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(GAS_VENT), player)) {
            helper.fail("Could not climb into the vent");
        }
        return player;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void hotGasThroughTheVentBurnsTheRider(GameTestHelper helper) {
        Player player = enterVentFedAt(helper, ZPSConfig.entityBurnGasTemperatureK() * 3.0);

        helper.runAfterDelay(WARM_UP_TICKS, () -> {
            helper.assertTrue(player.isOnFire(), "A rider in a vent passing hot gas should be on fire");
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "The rider should have been hurt");
            helper.succeed();
        });
    }

    /** Gas between the two thresholds does nothing to the rider either way. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void mildGasThroughTheVentLeavesTheRiderAlone(GameTestHelper helper) {
        double mild = (ZPSConfig.entityBurnGasTemperatureK() + ZPSConfig.entityFreezeGasTemperatureK()) / 2.0;
        Player player = enterVentFedAt(helper, mild);

        helper.runAfterDelay(WARM_UP_TICKS, () -> {
            helper.assertFalse(player.isOnFire(), "A rider in a vent passing mild gas should not be on fire");
            helper.assertTrue(player.getTicksFrozen() == 0, "A rider in a vent passing mild gas should not be freezing");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The rider should be unhurt");
            helper.succeed();
        });
    }

    /** No gas is neither hot nor cold. A vent nothing has passed through in a while does nothing. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anEmptyVentLeavesTheRiderAlone(GameTestHelper helper) {
        BlockPos vent = new BlockPos(3, 2, 3);
        helper.setBlock(vent, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.WEST));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(vent), player)) {
            helper.fail("Could not climb into the vent");
        }

        helper.runAfterDelay(WARM_UP_TICKS, () -> {
            helper.assertFalse(player.isOnFire(), "A rider in an empty vent should not be on fire");
            helper.assertTrue(player.getTicksFrozen() == 0, "A rider in an empty vent should not be freezing");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The rider should be unhurt");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void coldGasThroughTheVentFreezesTheRider(GameTestHelper helper) {
        Player player = enterVentFedAt(helper, ZPSConfig.entityFreezeGasTemperatureK() / 2.0);

        helper.runAfterDelay(WARM_UP_TICKS, () -> {
            helper.assertTrue(player.getTicksFrozen() > 0, "A rider in a vent passing cold gas should be freezing");
            helper.assertFalse(player.isOnFire(), "A freezing rider should not also be on fire");
            helper.succeed();
        });
    }

    // --- how long the crawl takes -------------------------------------------------------------

    /**
     * The screen is held black for the length of the journey, so this is the whole of how far
     * apart two vents feels. Asserted as properties rather than exact tick counts, because the
     * speed behind it is a config value that a pack is free to change.
     */
    @GameTest(template = TEMPLATE)
    public static void aLongerCrawlTakesLonger(GameTestHelper helper) {
        BlockPos origin = BlockPos.ZERO;
        double speed = ZPSConfig.ductTravelBlocksPerTick();

        // The next block along is the shortest journey there is: one block's crawl at the
        // configured speed, and never less than a tick.
        int neighbour = DuctTravelEntity.travelTicks(origin, origin.offset(0, 0, 1));
        int oneBlock = Math.max(1, (int) Math.ceil(1.0 / speed));
        if (neighbour != oneBlock) {
            helper.fail("A hop to the very next block should take one block's crawl of " + oneBlock
                    + " ticks, was " + neighbour);
        }

        // Far enough apart to be worth twenty ticks at whatever speed is configured.
        int far = DuctTravelEntity.travelTicks(origin, origin.offset(0, 0, (int) Math.ceil(speed * 20)));
        if (far <= neighbour) {
            helper.fail("A distant vent should take longer to reach than the next block along, "
                    + "but took " + far + " ticks against " + neighbour);
        }

        // Otherwise a pair of vents across a world would black the screen out for minutes.
        int absurd = DuctTravelEntity.travelTicks(origin, origin.offset(0, 0, 10_000_000));
        if (absurd != DuctTravelEntity.MAX_TRAVEL_TICKS) {
            helper.fail("However far apart two vents are the journey should cap at "
                    + DuctTravelEntity.MAX_TRAVEL_TICKS + " ticks, was " + absurd);
        }
        helper.succeed();
    }

    /**
     * Sitting in a vent is not the same as being between two. Only the crawl in between takes a
     * player out of the world; parked at a grille they are still there to be seen and hit, and a
     * flag that defaulted the wrong way would quietly make peeking invulnerable.
     */
    @GameTest(template = TEMPLATE)
    public static void sittingInAVentIsNotTravelling(GameTestHelper helper) {
        BlockPos vent = new BlockPos(3, 2, 3);
        helper.setBlock(vent, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.WEST));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(vent), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (DuctTravelEntity.isInTransit(player)) {
            helper.fail("A player parked in a vent should not count as being between two");
        }
        helper.succeed();
    }

    // --- climbing back out ------------------------------------------------------------------

    /** Climb into a vent facing {@code facing} and ask where dismounting would put the player. */
    private static Vec3 dismountFrom(GameTestHelper helper, BlockPos vent, Direction facing) {
        helper.setBlock(vent, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, facing));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!DuctTravelEntity.enter(helper.getLevel(), helper.absolutePos(vent), player)) {
            helper.fail("Could not climb into the vent");
        }
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            helper.fail("Climbing in left the player riding nothing");
            return Vec3.ZERO;
        }
        return duct.getDismountLocationForPassenger(player);
    }

    /**
     * A ceiling vent opens into the block a standing player's head would occupy, so coming out of
     * one has to drop a whole body rather than the one block every other facing needs.
     */
    @GameTest(template = TEMPLATE)
    public static void aCeilingVentDropsThePlayerClearOfIt(GameTestHelper helper) {
        BlockPos landingBlock = new BlockPos(3, 1, 3);
        // The two blocks a standing player would occupy under the vent.
        helper.setBlock(landingBlock, Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR.defaultBlockState());

        Vec3 landing = dismountFrom(helper, new BlockPos(3, 3, 3), Direction.DOWN);

        double expected = helper.absolutePos(landingBlock).getY();
        if (landing.y != expected) {
            helper.fail("A ceiling vent should leave the player standing clear of it at y "
                    + expected + ", but put them at y " + landing.y);
        }
        helper.succeed();
    }

    /**
     * A floor vent is a plate lying on the block beneath it, with its own block open above. Climbing
     * out of one means standing on the grille, not being lifted a block into the air over it.
     */
    @GameTest(template = TEMPLATE)
    public static void aFloorVentLeavesThePlayerStandingOnTheGrille(GameTestHelper helper) {
        BlockPos vent = new BlockPos(3, 2, 3);

        Vec3 landing = dismountFrom(helper, vent, Direction.UP);

        double expected = helper.absolutePos(vent).getY() + VentBlock.PLATE_THICKNESS / 16.0;
        if (landing.y != expected) {
            helper.fail("A floor vent should leave the player standing on its plate at y "
                    + expected + ", but put them at y " + landing.y);
        }
        helper.succeed();
    }

    /** The extra drop is for ceilings alone: a vent in a wall still puts them level with it. */
    @GameTest(template = TEMPLATE)
    public static void aWallVentLeavesThePlayerLevelWithIt(GameTestHelper helper) {
        BlockPos landingBlock = new BlockPos(3, 1, 2);
        helper.setBlock(landingBlock, Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR.defaultBlockState());

        Vec3 landing = dismountFrom(helper, new BlockPos(3, 1, 3), Direction.NORTH);

        double expected = helper.absolutePos(landingBlock).getY();
        if (landing.y != expected) {
            helper.fail("A wall vent should leave the player standing beside it at y "
                    + expected + ", but put them at y " + landing.y);
        }
        helper.succeed();
    }

    /**
     * The grille is the way out, not a way through. A duct pressed against it is part of some other
     * run — here one feeding a second vent from behind — and travelling into it would let a player
     * step straight through a sealed wall.
     */
    @GameTest(template = TEMPLATE)
    public static void theGrilleDoesNotConduct(GameTestHelper helper) {
        BlockPos vent = new BlockPos(3, 2, 3);
        BlockPos beyondTheGrille = vent.relative(Direction.WEST);
        BlockPos farSide = beyondTheGrille.relative(Direction.WEST);

        helper.setBlock(vent, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.WEST));
        helper.setBlock(beyondTheGrille, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(farSide, ModBlocks.VENT.get().defaultBlockState()
                .setValue(VentBlock.FACING, Direction.WEST));

        List<BlockPos> vents = DuctTravelNetwork.reachableVents(
                helper.getLevel(), helper.absolutePos(vent));
        if (contains(helper, vents, farSide)) {
            helper.fail("A vent should not conduct through its own grille");
        }
        helper.succeed();
    }
}
