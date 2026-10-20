package g_mungus.zps.blockentity.gas;

import net.minecraft.util.Mth;
import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.client.exhaust.ThrusterExhaustEmitter;
import g_mungus.zps.commands.phystick.PhysTickProgram;
import g_mungus.zps.commands.phystick.PhysTickProgrammable;
import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.HashMap;
import java.util.Map;

/**
 * Burns what reaches its node and pushes its grid with it. The throttle, a phys tick program's {@code thrust} in a
 * step that runs one and the redstone signal otherwise, asks for a share of {@link #MAX_MOL_PER_TICK}, taken from
 * the node's mix as it finds it, so a starved or low-pressure feed gives less than was asked and pushes less.
 * <p>
 * Stellane burns orange: it pushes at {@link #thrustPerMol()} and leaves as the same mass of hot
 * {@link ModGases#EXHAUST}. Flux burns blue the same way but pushes {@link #FLUX_PUSH_RATIO} as hard.
 * Anything else passes through unburnt at its own temperature, pushing only {@link #COLD_PUSH_RATIO}
 * as hard, and shows in the plume as cold gas. A mixed feed shows all three through one plume. All of
 * it is let out in front of the nozzle, into a room if there is one ({@link RoomManager}).
 * <p>
 * The physics step throttles and the server tick settles. Each tick the server works out the thrust a full burn
 * of the node's gas would give and hands it to the physics step as one immutable vector, since Valkyrien Skies
 * runs that step on a thread of its own; each step pushes with its throttle's share of it, so a program can
 * change the push between ticks. The next tick burns the gas for the throttle the steps used on average. In the
 * world, where nothing steps it, the tick burns for the throttle as it stands.
 */
public class ThrusterBlockEntity extends GasNodeBlockEntity implements PhysTickProgrammable, ThrusterExhaustEmitter {

    // --- tuning -----------------------------------------------------------------------------

    /** Gas asked for at a full signal, in moles per tick: half a kilogram of Stellane lasts 50 s. */
    public static final double MAX_MOL_PER_TICK = 0.25;
    /** Thrust at a full signal on pure Stellane, in {@link GridPhysics} units: a plain block weighs 1. */
    public static double maxThrust() {
        return ZPSConfig.thrusterMaxThrust();
    }

    public static double thrustPerMol() {
        return maxThrust() / MAX_MOL_PER_TICK;
    }
    /** How hard a mole of burning Flux pushes, against a mole of burning Stellane. */
    public static final double FLUX_PUSH_RATIO = 2;
    /** How hard a mole of unburnt gas pushes, against a mole of burning Stellane. */
    public static final double COLD_PUSH_RATIO = 0.5;
    /** Temperature the Exhaust leaves at, in kelvin. */
    public static final double BURN_TEMPERATURE_K = 1200;

    /*
     * Steering, by how far the thrust points off the line through the centre of mass, in degrees, 0 to 90 either
     * way along it. The push is always whole.
     * Up to STRAIGHT the thruster does not turn the grid at all and damps it in full (see DAMPING), so one nearly
     * lined up with the centre of mass drives it straight; from FULL_TURN it turns it as a force at the nozzle
     * would and does not damp, so it does not fight the drift a turn brings. Between, the turn fades in smoothly
     * and the damping out by as much.
     */
    public static final double STRAIGHT_DEGREES = 20;
    public static final double FULL_TURN_DEGREES = 35;

    /**
     * Damping: each unit of push through the centre of mass also resists sliding across the thrust and going
     * backwards against it, by this much per block per second of that speed, so a driven grid goes where it is
     * pointed and turns around quickly.
     */
    public static final double DAMPING = 1.0 / 64;
    /**
     * The fastest the damping may take speed off, as a share of it per second. Keeps a light grid with a big
     * thruster from overshooting into a wobble within one physics step.
     */
    public static final double MAX_DAMPING_RATE = 10;

    /** Below this many kilograms a share is not worth taking. */
    private static final double MIN_TRANSFER = 1e-9;
    /** Steps the burn and its blue and cold shares are sent to clients in. */
    private static final int SYNC_STEPS = 32;
    /** The burn a signal of 1 shows, the least any burn shows. */
    private static final int MIN_BURN_STEPS = (int) Math.round(SYNC_STEPS / 15.0);

    private static final String BURN_KEY = "Burn";
    private static final String BLUE_KEY = "Blue";
    private static final String COLD_KEY = "Cold";

    /** Ticks a grid's steps may skip, Valkyrien Skies' being out of step with ticks, before it counts as gone. */
    private static final int MISSED_STEP_TICKS = 2;

    /** Thrust at a full throttle for the physics step, in the grid's own axes; zero with nothing to burn. */
    private volatile Vec3 fullThrust = Vec3.ZERO;
    /** Thrust from what was burnt last tick, in the grid's own axes. */
    private volatile Vec3 thrust = Vec3.ZERO;
    /** The redstone signal, 0 to 1, as the server tick last read it. */
    private volatile double redstoneThrottle;
    /** What a phys tick program's {@code thrust} set this step, which stands in for the signal; NaN for none. */
    private volatile double programThrottle = Double.NaN;

    private final PhysTickProgram program = new PhysTickProgram(this::onPhysTickProgramStopped);
    private final ThrottleSamples samples = new ThrottleSamples();
    /** The steps' average throttle the last tick that had any, and how many ticks ago that was. */
    private double sampledThrottle;
    private int ticksSinceSampled = Integer.MAX_VALUE;

    /**
     * Gas drawn last tick against {@link #MAX_MOL_PER_TICK}, and the burning Flux and unburnt shares of it, in
     * {@link #SYNC_STEPS}.
     */
    private int burnSteps;
    private int blueSteps;
    private int coldSteps;

    public ThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THRUSTER.get(), pos, state);
    }

    public void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            tickExhaust();
            return;
        }

        redstoneThrottle = getBlockState().getValue(ThrusterBlock.POWER) / 15.0;
        burnFuel(settledThrottle());
        tickPhysTickProgram();
        syncNodeState();
    }

    /**
     * The throttle to burn for this tick: on a grid, what its physics steps used on average since the last tick;
     * in the world, what it stands at. A tick that a grid's steps skipped burns as the last one that had some.
     */
    private double settledThrottle() {
        ThrottleSamples.Average average = samples.take();
        if (average.steps() > 0) {
            sampledThrottle = average.throttle();
            ticksSinceSampled = 0;
            return sampledThrottle;
        }
        if (ticksSinceSampled < MISSED_STEP_TICKS) {
            ticksSinceSampled++;
            return sampledThrottle;
        }
        return throttle();
    }

    /** The throttle, 0 to 1: what a running phys tick program's {@code thrust} set this step, or else the signal's. */
    public double throttle() {
        double set = programThrottle;
        return program.isRunning() && !Double.isNaN(set) ? set : redstoneThrottle;
    }

    /** {@code thrust} in a phys tick program: the throttle for this step, in place of the signal. */
    public void setProgramThrottle(double throttle) {
        programThrottle = Mth.clamp(throttle, 0, 1);
    }

    @Override
    public PhysTickProgram physTickProgram() {
        return program;
    }

    @Override
    public void onPhysTickProgramStopped() {
        programThrottle = Double.NaN;
    }

    private void burnFuel(double throttle) {
        BlockState state = getBlockState();
        double requested = throttle * MAX_MOL_PER_TICK;
        Direction facing = state.getValue(ThrusterBlock.FACING);
        Vec3 backwards = Vec3.atLowerCornerOf(facing.getNormal()).scale(-1);

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos node = getDuctNodePosition();
        Map<GasType, Double> gases = Map.copyOf(getGases());
        double available = 0;
        double push = 0;
        for (Map.Entry<GasType, Double> entry : gases.entrySet()) {
            double moles = entry.getKey().massToMoles(entry.getValue());
            available += moles;
            push += pushPerMole(entry.getKey()) * moles;
        }
        // What a full burn would draw, each gas in proportion to its share as below.
        fullThrust = push > 0 ? backwards.scale(push * Math.min(1.0, MAX_MOL_PER_TICK / available)) : Vec3.ZERO;

        double stellane = 0;
        double flux = 0;
        double unburnt = 0;
        double exhaustKg = 0;
        Map<GasType, Double> passed = new HashMap<>();
        if (requested > 0 && available > 0) {
            // Each gas in proportion to its share, so a trace is not starved by an abundant one.
            double fraction = Math.min(1.0, requested / available);
            for (Map.Entry<GasType, Double> entry : gases.entrySet()) {
                GasType gas = entry.getKey();
                double take = entry.getValue() * fraction;
                if (take <= MIN_TRANSFER || !kelvin.removeGas(node, gas, take)) {
                    continue;
                }
                if (gas.equals(ModGases.STELLANE)) {
                    stellane += gas.massToMoles(take);
                    exhaustKg += take;
                } else if (gas.equals(ModGases.FLUX)) {
                    flux += gas.massToMoles(take);
                    exhaustKg += take;
                } else {
                    unburnt += gas.massToMoles(take);
                    passed.merge(gas, take, Double::sum);
                }
            }
        }

        double force = (stellane + FLUX_PUSH_RATIO * flux + COLD_PUSH_RATIO * unburnt) * thrustPerMol();
        thrust = force > 0 ? backwards.scale(force) : Vec3.ZERO;

        if (level instanceof ServerLevel serverLevel) {
            BlockPos front = worldPosition.relative(facing);
            RoomManager rooms = RoomManager.get(serverLevel);
            if (exhaustKg > 0) {
                rooms.addGas(serverLevel, front, Map.of(ModGases.EXHAUST, exhaustKg), BURN_TEMPERATURE_K);
            }
            if (!passed.isEmpty()) {
                rooms.addGas(serverLevel, front, passed, getTemperature());
            }
        }

        double drawn = stellane + flux + unburnt;
        // Any burn at all shows at least the plume of a signal of 1, so a thruster never pushes without one.
        int burn = (int) Math.round(Math.min(1.0, drawn / MAX_MOL_PER_TICK) * SYNC_STEPS);
        setSynced(drawn > 0 ? Math.max(burn, MIN_BURN_STEPS) : 0,
                drawn > 0 ? (int) Math.round(flux / drawn * SYNC_STEPS) : 0,
                drawn > 0 ? (int) Math.round(unburnt / drawn * SYNC_STEPS) : 0);
    }

    /** The thrust a mole of {@code gas} gives as it goes through. */
    private static double pushPerMole(GasType gas) {
        if (gas.equals(ModGases.STELLANE)) {
            return thrustPerMol();
        } else if (gas.equals(ModGases.FLUX)) {
            return FLUX_PUSH_RATIO * thrustPerMol();
        }
        return COLD_PUSH_RATIO * thrustPerMol();
    }

    private void setSynced(int burn, int blue, int cold) {
        if (burn == burnSteps && blue == blueSteps && cold == coldSteps) {
            return;
        }
        burnSteps = burn;
        blueSteps = blue;
        coldSteps = cold;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Gas drawn last tick, 0 to 1 of a full signal's worth: the throttle the feed actually allowed. */
    public float burn() {
        return burnSteps / (float) SYNC_STEPS;
    }

    /** The burning Flux share of what was drawn last tick, by moles. */
    public float blueFraction() {
        return blueSteps / (float) SYNC_STEPS;
    }

    /** The unburnt share of what was drawn last tick, by moles. */
    public float coldFraction() {
        return coldSteps / (float) SYNC_STEPS;
    }

    /** The thrust from what was burnt last tick, in the grid's own axes. */
    public Vec3 thrust() {
        return thrust;
    }

    /** The thrust a full throttle gives from the node's gas, as handed to the physics step. */
    public Vec3 fullThrust() {
        return fullThrust;
    }

    // --- physics ----------------------------------------------------------------------------

    /**
     * Runs the phys tick program, then pushes the grid with the throttle's share of the full thrust through the
     * centre of mass, damps its drift and going backwards ({@link #damping}) as much as {@link #dampingShare}
     * allows, and turns it as much as {@link #turn} allows. The throttle is noted for the next tick to burn for.
     */
    @Override
    public void onGridPhysicsTick(GridPhysics physics) {
        // A step whose program runs no thrust goes by the signal.
        programThrottle = Double.NaN;
        runPhysTickProgram(physics);
        double throttle = throttle();
        samples.add(throttle);
        Vec3 force = fullThrust.scale(throttle);
        if (force.lengthSqr() == 0) {
            return;
        }
        Vec3 centreOfMass = physics.centreOfMass();
        Vec3 lever = Vec3.atCenterOf(worldPosition).subtract(centreOfMass);
        Vec3 velocity = physics.toGridDirection(physics.linearVelocity());
        Vec3 damping = damping(force, velocity, physics.mass()).scale(dampingShare(lever, force));
        physics.applyForce(centreOfMass, force.add(damping));
        Vec3 turn = turn(lever, force);
        if (turn.lengthSqr() > 0) {
            physics.applyTorque(turn);
        }
    }

    /**
     * The torque a thrust turns its grid with: the {@code lever x force} a force at the nozzle would give, except
     * that a thruster nearly lined up with the centre of mass is taken to be there to drive the grid straight and
     * turns it less or not at all, see {@link #STRAIGHT_DEGREES}.
     *
     * @param lever from the centre of mass to where the thrust acts
     * @param force the thrust
     */
    public static Vec3 turn(Vec3 lever, Vec3 force) {
        return lever.cross(force).scale(turnShare(lever, force));
    }

    /** How much of a force at the nozzle's turn a thruster applies, 0 to 1; see {@link #STRAIGHT_DEGREES}. */
    public static double turnShare(Vec3 lever, Vec3 force) {
        return smoothstep(STRAIGHT_DEGREES, FULL_TURN_DEGREES, offAxisDegrees(lever, force));
    }

    /**
     * How much of {@link #damping} a thruster applies, 0 to 1: whatever {@link #turnShare} leaves, so one there to
     * drive the grid straight damps in full and one there to turn it not at all.
     */
    public static double dampingShare(Vec3 lever, Vec3 force) {
        return 1 - turnShare(lever, force);
    }

    /**
     * How far {@code force} points off the line through the centre of mass, 0 to 90 degrees: folded so pushing
     * towards the centre of mass reads the same as pushing away from it. A thruster on the centre of mass, with no
     * lever, counts as lined up.
     */
    private static double offAxisDegrees(Vec3 lever, Vec3 force) {
        double leverLength = lever.length();
        double forceLength = force.length();
        if (forceLength == 0 || leverLength < 1e-6) {
            return 0;
        }
        double alignment = Math.min(1, Math.abs(lever.dot(force)) / (leverLength * forceLength));
        return Math.toDegrees(Math.acos(alignment));
    }

    /**
     * The force against going anywhere but where a push points: against the velocity off the push's axis, and
     * against any going backwards along it, which adds to the push. Going forwards is left alone. In proportion to
     * the push ({@link #DAMPING}), but never faster than {@link #MAX_DAMPING_RATE} allows for the mass.
     *
     * @param push     the push through the centre of mass
     * @param velocity the centre of mass's velocity, in the same axes as {@code push}
     */
    public static Vec3 damping(Vec3 push, Vec3 velocity, double mass) {
        double pushLength = push.length();
        if (pushLength == 0) {
            return Vec3.ZERO;
        }
        Vec3 axis = push.scale(1 / pushLength);
        double forwards = velocity.dot(axis);
        Vec3 resisted = velocity.subtract(axis.scale(Math.max(forwards, 0)));
        double coefficient = Math.min(pushLength * DAMPING, mass * MAX_DAMPING_RATE);
        return resisted.scale(-coefficient);
    }

    /** 0 up to {@code from}, 1 from {@code to}, and an S-curve between with no kink at either end. */
    private static double smoothstep(double from, double to, double x) {
        double t = Mth.clamp((x - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** The throttles the physics steps used since the last tick, which the steps add to and the tick takes. */
    private static final class ThrottleSamples {
        private double sum;
        private int steps;

        synchronized void add(double throttle) {
            sum += throttle;
            steps++;
        }

        synchronized Average take() {
            Average average = new Average(steps == 0 ? 0 : sum / steps, steps);
            sum = 0;
            steps = 0;
            return average;
        }

        record Average(double throttle, int steps) {
        }
    }

    // --- exhaust ----------------------------------------------------------------------------

    @Override
    public @Nullable ExhaustParams getCurrentExhaust() {
        float burn = burn();
        if (burn <= 0) {
            return null;
        }
        Vec3 direction = Vec3.atLowerCornerOf(getBlockState().getValue(ThrusterBlock.FACING).getNormal());
        return new ExhaustParams(
                burn,
                0.5f,
                0.3f + 0.7f * burn,
                Vec3.atCenterOf(worldPosition).add(direction.scale(0.55)),
                direction,
                ExhaustColor.ORANGE,
                blueFraction(),
                coldFraction()
        );
    }

    // --- client sync ------------------------------------------------------------------------

    /** Only the burn goes to clients; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putByte(BURN_KEY, (byte) burnSteps);
        tag.putByte(BLUE_KEY, (byte) blueSteps);
        tag.putByte(COLD_KEY, (byte) coldSteps);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        burnSteps = tag.getByte(BURN_KEY);
        blueSteps = tag.getByte(BLUE_KEY);
        coldSteps = tag.getByte(COLD_KEY);
    }
}
