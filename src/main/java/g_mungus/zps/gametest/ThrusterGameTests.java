package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.ThrusterBlockEntity;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.commands.phystick.PhysTickProgrammableTarget;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The thruster: the signal asks for gas in fifteenths, Stellane burns into Exhaust and pushes, Flux burns and pushes
 * twice as hard, other gas passes through cold and pushes half as hard, and the push reaches the grid's physics step pointing away from the nozzle.
 * Gas is put straight into the thruster's node, so nothing but the thruster takes it out.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThrusterGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final String ROOM_TEMPLATE = "gametest/flat_11x9x11";

    private static final BlockPos THRUSTER = new BlockPos(3, 2, 3);
    /** Far more of each gas than a few ticks at a full signal ask for. */
    private static final double MOLES = 10;
    private static final int BURN_TICKS = 5;

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static ThrusterBlockEntity placeThruster(GameTestHelper helper, BlockPos at, Direction facing, int power) {
        helper.setBlock(at, ModBlocks.THRUSTER.get().defaultBlockState()
                .setValue(ThrusterBlock.FACING, facing)
                .setValue(ThrusterBlock.POWER, power));
        if (!(helper.getBlockEntity(at) instanceof ThrusterBlockEntity thruster)) {
            helper.fail("The thruster has no block entity");
            throw new IllegalStateException();
        }
        return thruster;
    }

    private static void feed(GameTestHelper helper, BlockPos at, GasType gas, double moles) {
        KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(node(helper, at), gas, gas.molesToMass(moles), 300.0);
    }

    private static double massOf(GameTestHelper helper, BlockPos at, GasType gas) {
        return KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(node(helper, at)).getOrDefault(gas, 0.0);
    }

    private static void assertThrust(GameTestHelper helper, ThrusterBlockEntity thruster, double expected, String what) {
        // Facing south, so it pushes north.
        Vec3 thrust = thruster.thrust();
        helper.assertTrue(thrust.distanceTo(new Vec3(0, 0, -expected)) < expected * 1e-6 + 1e-9,
                what + ": the thrust should be " + expected + " northward, is " + thrust);
    }

    @GameTest(template = TEMPLATE)
    public static void unpowered_burnsNothing(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        double before = massOf(helper, THRUSTER, ModGases.STELLANE);
        helper.runAfterDelay(BURN_TICKS, () -> {
            helper.assertTrue(massOf(helper, THRUSTER, ModGases.STELLANE) == before, "An unpowered thruster should draw nothing");
            helper.assertTrue(thruster.thrust().lengthSqr() == 0, "An unpowered thruster should not push");
            helper.assertTrue(thruster.burn() == 0, "An unpowered thruster should show no plume");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fullSignalOnStellane_pushesAtFullThrust(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            assertThrust(helper, thruster, ThrusterBlockEntity.maxThrust(), "Pure Stellane at 15");
            helper.assertTrue(thruster.burn() == 1, "A fed thruster at 15 should burn at full, burns " + thruster.burn());
            helper.assertTrue(thruster.coldFraction() == 0, "Pure Stellane should leave nothing cold");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void signalFive_asksForAThird(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 5);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        double before = massOf(helper, THRUSTER, ModGases.STELLANE);
        helper.runAfterDelay(BURN_TICKS, () -> {
            assertThrust(helper, thruster, ThrusterBlockEntity.maxThrust() / 3, "Pure Stellane at 5");
            double burnt = ModGases.STELLANE.massToMoles(before - massOf(helper, THRUSTER, ModGases.STELLANE));
            double perTick = ThrusterBlockEntity.MAX_MOL_PER_TICK / 3;
            // Whole ticks only, and the block entity may have ticked once more or less than the delay.
            double ticks = burnt / perTick;
            helper.assertTrue(Math.abs(ticks - Math.round(ticks)) < 1e-6 && Math.abs(ticks - BURN_TICKS) <= 1,
                    "At 5 the thruster should burn " + perTick + " mol a tick, burnt " + burnt + " in " + BURN_TICKS);
            helper.succeed();
        });
    }

    /** Half Steam by moles: half the full burn, half as much again for the cold half, and a half-cold plume. */
    @GameTest(template = TEMPLATE)
    public static void impureFeed_pushesLessAndShowsCold(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        feed(helper, THRUSTER, ModGases.STEAM, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            double half = ThrusterBlockEntity.maxThrust() / 2;
            assertThrust(helper, thruster, half + half * ThrusterBlockEntity.COLD_PUSH_RATIO, "Half Stellane, half Steam");
            helper.assertTrue(thruster.burn() == 1, "The mix should still be drawn at full, is " + thruster.burn());
            helper.assertTrue(thruster.coldFraction() == 0.5f, "Half the plume should be cold, is " + thruster.coldFraction());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pureFlux_pushesTwiceAsHardAndBurnsBlue(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.FLUX, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            assertThrust(helper, thruster, ThrusterBlockEntity.maxThrust() * ThrusterBlockEntity.FLUX_PUSH_RATIO, "Pure Flux");
            helper.assertTrue(thruster.blueFraction() == 1, "Pure Flux should be all blue, is " + thruster.blueFraction());
            helper.assertTrue(thruster.coldFraction() == 0, "Pure Flux should leave nothing cold");
            helper.succeed();
        });
    }

    /** A quarter Flux, a quarter Steam by moles: every share pushes at its own rate and shows in one plume. */
    @GameTest(template = TEMPLATE)
    public static void threeWayMix_showsAllThree(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES * 2);
        feed(helper, THRUSTER, ModGases.FLUX, MOLES);
        feed(helper, THRUSTER, ModGases.STEAM, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            double quarter = ThrusterBlockEntity.maxThrust() / 4;
            assertThrust(helper, thruster, quarter * (2 + ThrusterBlockEntity.FLUX_PUSH_RATIO
                    + ThrusterBlockEntity.COLD_PUSH_RATIO), "Half Stellane, a quarter Flux, a quarter Steam");
            helper.assertTrue(thruster.blueFraction() == 0.25f, "A quarter of the plume should be blue, is " + thruster.blueFraction());
            helper.assertTrue(thruster.coldFraction() == 0.25f, "A quarter of the plume should be cold, is " + thruster.coldFraction());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void noStellane_stillPushesALittle(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.STEAM, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            assertThrust(helper, thruster, ThrusterBlockEntity.maxThrust() * ThrusterBlockEntity.COLD_PUSH_RATIO, "Pure Steam");
            helper.assertTrue(thruster.coldFraction() == 1, "Pure Steam should be all cold");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void starved_pushesWhatItGets(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        helper.runAfterDelay(BURN_TICKS, () -> {
            helper.assertTrue(thruster.thrust().lengthSqr() == 0, "A thruster with no gas should not push");
            helper.assertTrue(thruster.burn() == 0, "A thruster with no gas should show no plume");
            helper.succeed();
        });
    }

    /**
     * Runs one physics step of a thruster blowing up, pushing down at full thrust, with the grid's centre of mass
     * {@code offset} from the thruster's centre.
     */
    private static void stepWithCentreOfMassAt(GameTestHelper helper, Vec3 offset, Consumer<RecordingPhysics> check) {
        stepMoving(helper, offset, Vec3.ZERO, 1, check);
    }

    /** As {@link #stepWithCentreOfMassAt}, the grid moving at {@code velocity} and weighing {@code mass}. */
    private static void stepMoving(GameTestHelper helper, Vec3 offset, Vec3 velocity, double mass,
                                   Consumer<RecordingPhysics> check) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.UP, 15);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            RecordingPhysics physics = new RecordingPhysics(
                    Vec3.atCenterOf(helper.absolutePos(THRUSTER)).add(offset), velocity, mass);
            thruster.onGridPhysicsTick(physics);
            check.accept(physics);
            helper.succeed();
        });
    }

    private static Vec3 downThrust() {
        return new Vec3(0, -ThrusterBlockEntity.maxThrust(), 0);
    }

    /** Lined up with the centre of mass: the whole thrust through it, and no turn. */
    @GameTest(template = TEMPLATE)
    public static void thrustThroughTheCentreOfMass_onlyPushes(GameTestHelper helper) {
        // Just off the line, inside the push-only zone.
        stepWithCentreOfMassAt(helper, new Vec3(1, -5, 0), physics -> {
            helper.assertTrue(physics.forces.size() == 1, "One force should be applied, " + physics.forces.size() + " were");
            helper.assertTrue(physics.forces.getFirst()[0].equals(physics.centreOfMass()),
                    "The force should act at the centre of mass, acts at " + physics.forces.getFirst()[0]);
            helper.assertTrue(physics.forces.getFirst()[1].distanceTo(downThrust()) < 1e-6,
                    "The whole thrust should push, pushes " + physics.forces.getFirst()[1]);
            helper.assertTrue(physics.torques.isEmpty(), "Nothing should turn the grid, turned by " + physics.torques);
        });
    }

    /** Square across the centre of mass: the whole push, and the whole turn the thrust would give. */
    @GameTest(template = TEMPLATE)
    public static void thrustAcrossTheCentreOfMass_pushesAndTurns(GameTestHelper helper) {
        Vec3 offset = new Vec3(5, -1, 0);
        stepWithCentreOfMassAt(helper, offset, physics -> {
            helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(downThrust()) < 1e-6,
                    "The whole thrust should push through the centre of mass");
            helper.assertTrue(physics.torques.size() == 1, "One torque should be applied, " + physics.torques.size() + " were");
            Vec3 expected = offset.reverse().cross(downThrust());
            helper.assertTrue(physics.torques.getFirst().distanceTo(expected) < 1e-6,
                    "The turn should be lever x thrust, " + expected + ", is " + physics.torques.getFirst());
        });
    }

    /** At 45 degrees: the push and turn of a force at the thruster, as if nothing were guessed. */
    @GameTest(template = TEMPLATE)
    public static void thrustAtFortyFive_pushesAndTurnsInFull(GameTestHelper helper) {
        Vec3 offset = new Vec3(5, -5, 0);
        stepWithCentreOfMassAt(helper, offset, physics -> {
            helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(downThrust()) < 1e-6,
                    "The whole thrust should push");
            helper.assertTrue(physics.torques.size() == 1
                            && physics.torques.getFirst().distanceTo(offset.reverse().cross(downThrust())) < 1e-6,
                    "The whole turn should be applied, is " + physics.torques);
        });
    }

    /** A driven grid sliding sideways is pushed back against the slide, in proportion to the thrust. */
    @GameTest(template = TEMPLATE)
    public static void drivenGridSlidingSideways_isDamped(GameTestHelper helper) {
        // Moving with the thrust and sideways: only the sideways part is resisted.
        stepMoving(helper, new Vec3(0, -5, 0), new Vec3(3, -2, 0), 1000, physics -> {
            double coefficient = ThrusterBlockEntity.maxThrust() * ThrusterBlockEntity.DAMPING;
            Vec3 expected = downThrust().add(-3 * coefficient, 0, 0);
            helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(expected) < 1e-6,
                    "The push should carry " + expected + ", carries " + physics.forces.getFirst()[1]);
        });
    }

    /** A driven grid going backwards against its thrust is pushed forwards harder, in proportion to the thrust. */
    @GameTest(template = TEMPLATE)
    public static void drivenGridGoingBackwards_isPushedHarder(GameTestHelper helper) {
        // Thrust is down; moving up against it, and sideways.
        stepMoving(helper, new Vec3(0, -5, 0), new Vec3(3, 2, 0), 1000, physics -> {
            double coefficient = ThrusterBlockEntity.maxThrust() * ThrusterBlockEntity.DAMPING;
            Vec3 expected = downThrust().add(-3 * coefficient, -2 * coefficient, 0);
            helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(expected) < 1e-6,
                    "The push should carry " + expected + ", carries " + physics.forces.getFirst()[1]);
        });
    }

    /** On a light grid the damping is held to a rate that cannot overshoot in a step. */
    @GameTest(template = TEMPLATE)
    public static void dampingOnALightGrid_isCapped(GameTestHelper helper) {
        // Half the mass at which full thrust's damping reaches the cap.
        double mass = ThrusterBlockEntity.maxThrust() * ThrusterBlockEntity.DAMPING / ThrusterBlockEntity.MAX_DAMPING_RATE / 2;
        stepMoving(helper, new Vec3(0, -5, 0), new Vec3(3, 0, 0), mass, physics -> {
            Vec3 expected = downThrust().add(-3 * mass * ThrusterBlockEntity.MAX_DAMPING_RATE, 0, 0);
            helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(expected) < 1e-6,
                    "The push should carry " + expected + ", carries " + physics.forces.getFirst()[1]);
        });
    }

    /** A thruster square across the centre of mass is there to turn: it pushes but does not fight the drift. */
    @GameTest(template = TEMPLATE)
    public static void turningThruster_doesNotDamp(GameTestHelper helper) {
        stepMoving(helper, new Vec3(5, -1, 0), new Vec3(0, 0, 3), 1000, physics ->
                helper.assertTrue(physics.forces.size() == 1 && physics.forces.getFirst()[1].distanceTo(downThrust()) < 1e-6,
                        "Only the thrust should push, with no damping, pushes " + physics.forces.getFirst()[1]));
    }

    /** Between straight and full the turn fades in smoothly and the damping out: halfway through, half of each. */
    @GameTest(template = TEMPLATE)
    public static void steering_fadesBetweenTheZones(GameTestHelper helper) {
        double halfway = Math.toRadians((ThrusterBlockEntity.STRAIGHT_DEGREES + ThrusterBlockEntity.FULL_TURN_DEGREES) / 2);
        Vec3 force = new Vec3(0, -1, 0);
        Vec3 lever = new Vec3(Math.sin(halfway), -Math.cos(halfway), 0);
        double whole = lever.cross(force).length();
        helper.assertTrue(Math.abs(ThrusterBlockEntity.turn(lever, force).length() - whole / 2) < 1e-9,
                "Half the turn should be applied, is " + ThrusterBlockEntity.turn(lever, force));
        helper.assertTrue(Math.abs(ThrusterBlockEntity.dampingShare(lever, force) - 0.5) < 1e-9,
                "Half the damping should be applied, is " + ThrusterBlockEntity.dampingShare(lever, force));
        // Pushing towards the centre of mass reads the same as pushing away from it.
        helper.assertTrue(Math.abs(ThrusterBlockEntity.turn(lever.reverse(), force).length() - whole / 2) < 1e-9,
                "A thruster across the centre of mass should turn the same");
        helper.succeed();
    }

    /** A thruster in the wall of a sealed box, nozzle in: what it burns goes into the box as Exhaust. */
    @GameTest(template = ROOM_TEMPLATE)
    public static void burntStellane_leavesAsExhaust(GameTestHelper helper) {
        BlockPos min = new BlockPos(3, 1, 3);
        BlockPos max = new BlockPos(7, 5, 7);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    boolean wall = x == min.getX() || x == max.getX() || y == min.getY() || y == max.getY()
                            || z == min.getZ() || z == max.getZ();
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos inside = new BlockPos(5, 3, 5);
        RoomManager rooms = RoomManager.get(helper.getLevel());
        // The test world is reused between runs, and a room saved by an earlier run would still be here.
        Room stale = rooms.knownRoomAt(helper.absolutePos(inside));
        if (stale != null) {
            rooms.dissolve(stale);
        }
        BlockPos wall = new BlockPos(3, 3, 5);
        ThrusterBlockEntity thruster = placeThruster(helper, wall, Direction.EAST, 15);
        feed(helper, wall, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            // One thruster's Exhaust is a trickle the room's leak soon settles, which puts it to sleep; look straight
            // after a burn, before the room has ticked.
            thruster.tick();
            Room room = rooms.roomAt(helper.absolutePos(inside));
            helper.assertTrue(room != null, "The burn should have gone into the box as a room");
            double exhaust = room.massKg().getOrDefault(ModGases.EXHAUST.getResourceLocation(), 0.0);
            helper.assertTrue(exhaust > 0, "The room should hold Exhaust, holds " + room.massKg());
            helper.assertTrue(!room.massKg().containsKey(ModGases.STELLANE.getResourceLocation()),
                    "No Stellane should come out unburnt, the room holds " + room.massKg());
            helper.succeed();
        });
    }

    // --- phys tick programs ------------------------------------------------------------------

    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
    }

    private static void program(GameTestHelper helper, ThrusterBlockEntity thruster, String command) {
        thruster.physTickProgram().accept(command, source(helper), helper.getLevel(), helper.absolutePos(THRUSTER));
    }

    /** One physics step of an unturned, unmoving grid at the thruster; returns the force it pushed with. */
    private static Vec3 step(GameTestHelper helper, ThrusterBlockEntity thruster, Vec3 velocity) {
        RecordingPhysics physics = new RecordingPhysics(Vec3.atCenterOf(helper.absolutePos(THRUSTER)), velocity, 1);
        thruster.onGridPhysicsTick(physics);
        return physics.forces.isEmpty() ? Vec3.ZERO : physics.forces.get(0)[1];
    }

    /** {@code thrust} in a program stands in for the signal: half pushes half, and the next tick burns half. */
    @GameTest(template = TEMPLATE)
    public static void programThrottle_pushesItsShareAndBurnsForIt(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            program(helper, thruster, "thrust 0.5");
            Vec3 force = step(helper, thruster, Vec3.ZERO);
            double half = ThrusterBlockEntity.maxThrust() / 2;
            helper.assertTrue(force.distanceTo(new Vec3(0, 0, -half)) < 1e-6,
                    "Half a throttle should push " + half + " northward, pushes " + force);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(thruster.burn() == 0.5f, "The tick after should burn for half, burns " + thruster.burn());
                helper.succeed();
            });
        });
    }

    /** However little it burns, a burning thruster shows the plume of a signal of 1. */
    @GameTest(template = TEMPLATE)
    public static void tinyBurn_stillShowsAPlume(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            program(helper, thruster, "thrust 0.01");
            helper.assertTrue(step(helper, thruster, Vec3.ZERO).lengthSqr() > 0,
                    "A hundredth of a throttle should still push");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(thruster.burn() >= Math.round(32 / 15.0) / 32f,
                        "A tiny burn should show at least a signal of 1's plume, shows " + thruster.burn());
                helper.succeed();
            });
        });
    }

    /**
     * A program that fails in a step stops, and the next send of it starts it again but fails with the reason, so
     * the Serial Bus that sent it shows why. A program sent in its place has nothing to report.
     */
    @GameTest(template = TEMPLATE)
    public static void program_failureIsReportedByTheNextSend(GameTestHelper helper) {
        if (!Compat.isSableLoaded() && !Compat.isVSLoaded()) {
            helper.succeed();
            return;
        }
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        helper.runAfterDelay(1, () -> {
            String command = "thrust value_of(ship local_vel y)";
            program(helper, thruster, command);
            // A step with no velocity to read fails the ship words.
            step(helper, thruster, null);
            helper.assertTrue(!thruster.physTickProgram().isRunning(), "A failing program should stop");
            String reason = null;
            try {
                program(helper, thruster, command);
            } catch (IllegalStateException e) {
                reason = e.getMessage();
            }
            helper.assertTrue(reason != null && reason.startsWith("The phys tick program stopped"),
                    "Sending it again should report the failure, got " + reason);
            helper.assertTrue(thruster.physTickProgram().isRunning(), "and start it again all the same");
            program(helper, thruster, command);

            step(helper, thruster, null);
            program(helper, thruster, "thrust 1");
            helper.assertTrue(thruster.physTickProgram().isRunning(),
                    "Another program should replace a failed one without reporting it");
            helper.succeed();
        });
    }

    /**
     * A program whose block is on no grid has no steps to run in, so the next server tick stops it and hands the
     * throttle back to the signal, as when a thruster leaves its ship, without waiting for it to go stale.
     */
    @GameTest(template = TEMPLATE)
    public static void program_stopsOffAGrid(GameTestHelper helper) {
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        helper.runAfterDelay(1, () -> {
            program(helper, thruster, "thrust 1");
            step(helper, thruster, Vec3.ZERO);
            helper.assertTrue(thruster.throttle() == 1, "The program should set the throttle, is " + thruster.throttle());
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!thruster.physTickProgram().isRunning(), "The program should have stopped");
                helper.assertTrue(thruster.throttle() == 0, "A stopped program should leave the signal's throttle, is "
                        + thruster.throttle());
                helper.succeed();
            });
        });
    }

    /** A step whose program runs no {@code thrust} goes by the signal; one that runs {@code thrust 0} does not. */
    @GameTest(template = TEMPLATE)
    public static void program_withoutThrustLeavesTheSignal(GameTestHelper helper) {
        if (!Compat.isSableLoaded() && !Compat.isVSLoaded()) {
            helper.succeed();
            return;
        }
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 15);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            program(helper, thruster, "if ship local_vel y > 0 thrust 0");
            helper.assertTrue(step(helper, thruster, new Vec3(0, 1, 0)).lengthSqr() == 0,
                    "Rising, thrust 0 should cut the signal");
            Vec3 falling = step(helper, thruster, new Vec3(0, -1, 0));
            helper.assertTrue(Math.abs(falling.z + ThrusterBlockEntity.maxThrust()) < 1e-6,
                    "Falling, with no thrust run, the full signal should push, pushes " + falling);
            helper.succeed();
        });
    }

    /** The ship words read the step itself, so a program can answer to the motion of every step. */
    @GameTest(template = TEMPLATE)
    public static void program_readsTheStepItRunsIn(GameTestHelper helper) {
        if (!Compat.isSableLoaded() && !Compat.isVSLoaded()) {
            helper.succeed();
            return;
        }
        ThrusterBlockEntity thruster = placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        feed(helper, THRUSTER, ModGases.STELLANE, MOLES);
        helper.runAfterDelay(BURN_TICKS, () -> {
            program(helper, thruster, "if ship local_vel y > 0 thrust 0 else thrust 1");
            helper.assertTrue(step(helper, thruster, new Vec3(0, 1, 0)).lengthSqr() == 0,
                    "Rising, the program should cut the throttle");
            Vec3 falling = step(helper, thruster, new Vec3(0, -1, 0));
            helper.assertTrue(Math.abs(falling.z + ThrusterBlockEntity.maxThrust()) < 1e-6,
                    "Falling, the program should open the throttle, pushes " + falling);
            helper.succeed();
        });
    }

    /** {@code on_phys_tick} is refused where there is no physics step to run in. */
    @GameTest(template = TEMPLATE)
    public static void onPhysTick_isRefusedInTheWorld(GameTestHelper helper) {
        placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        ScriptCommandFailure failure = ZPSScripts.get().tryRun("on_phys_tick command(thrust 1)", source(helper),
                helper.absolutePos(THRUSTER));
        helper.assertTrue(failure != null, "on_phys_tick should fail on a thruster in the world");
        ScriptCommandFailure outside = ZPSScripts.get().tryRun("thrust 0.5", source(helper),
                helper.absolutePos(THRUSTER));
        helper.assertTrue(outside != null, "thrust should fail outside on_phys_tick");
        helper.succeed();
    }

    /** Only executors meant for a program parse in one: anything else is refused before it can run. */
    @GameTest(template = TEMPLATE)
    public static void onPhysTick_refusesOtherExecutors(GameTestHelper helper) {
        placeThruster(helper, THRUSTER, Direction.SOUTH, 0);
        List<String> commands = new ArrayList<>(List.of("on_phys_tick command(set_redstone 15)",
                "on_phys_tick command(on_phys_tick command(thrust 1))"));
        if (Compat.isSableLoaded() || Compat.isVSLoaded()) {
            commands.add("on_phys_tick command(if ship mass > 0 thrust 1 else set_redstone 0)");
        }
        for (String command : commands) {
            ScriptCommandFailure failure = ZPSScripts.get().tryRun(command, source(helper), helper.absolutePos(THRUSTER));
            helper.assertTrue(failure != null && failure.reason().contains("cannot be used inside on_phys_tick"),
                    "'" + command + "' should be refused inside on_phys_tick, got " + failure);
        }
        helper.succeed();
    }

    /** The thruster's page lists {@code on_phys_tick}, which is for any block that takes a program. */
    @GameTest(template = TEMPLATE)
    public static void thruster_isListedAsTakingPrograms(GameTestHelper helper) {
        helper.assertTrue(PhysTickProgrammableTarget.INSTANCE.listedBlocks().contains(ZPSMod.resource("thruster")),
                "The thruster should be listed for on_phys_tick, the list is "
                        + PhysTickProgrammableTarget.INSTANCE.listedBlocks());
        helper.succeed();
    }

    /** Records what is applied; the grid is not turned, so its axes are the world's. */
    private static final class RecordingPhysics implements GridPhysics {
        private final Vec3 centreOfMass;
        private final Vec3 velocity;
        private final double mass;
        final List<Vec3[]> forces = new ArrayList<>();
        final List<Vec3> torques = new ArrayList<>();

        RecordingPhysics(Vec3 centreOfMass, Vec3 velocity, double mass) {
            this.centreOfMass = centreOfMass;
            this.velocity = velocity;
            this.mass = mass;
        }

        @Override
        public void applyForce(Vec3 point, Vec3 force) {
            forces.add(new Vec3[]{point, force});
        }

        @Override
        public void applyTorque(Vec3 torque) {
            torques.add(torque);
        }

        @Override
        public Vec3 linearVelocity() {
            return velocity;
        }

        @Override
        public Vec3 angularVelocity() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 position() {
            return centreOfMass;
        }

        @Override
        public double mass() {
            return mass;
        }

        @Override
        public Vec3 centreOfMass() {
            return centreOfMass;
        }

        @Override
        public Vec3 toWorldDirection(Vec3 direction) {
            return direction;
        }

        @Override
        public Vec3 toGridDirection(Vec3 direction) {
            return direction;
        }
    }
}
