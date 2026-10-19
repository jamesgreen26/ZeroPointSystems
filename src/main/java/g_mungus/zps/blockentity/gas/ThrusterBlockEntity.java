package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.client.exhaust.ThrusterExhaustEmitter;
import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.compat.GridPhysicsActor;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 * Burns what reaches its node and pushes its grid with it. Each tick the redstone signal asks for a
 * share of {@link #MAX_MOL_PER_TICK}, taken from the node's mix as it finds it, so a starved or
 * low-pressure feed gives less than was asked and pushes less.
 * <p>
 * Stellane burns: it pushes at {@link #THRUST_PER_MOL} and leaves as the same mass of hot
 * {@link ModGases#EXHAUST}. Anything else passes through unburnt at its own temperature, pushing only
 * {@link #COLD_PUSH_RATIO} as hard, and shows in the plume as cold gas. Both are let out in front of
 * the nozzle, into a room if there is one ({@link RoomManager}).
 * <p>
 * The burn is worked out on the server tick and handed to the physics step as one immutable vector,
 * since Valkyrien Skies runs that step on a thread of its own.
 */
public class ThrusterBlockEntity extends GasNodeBlockEntity implements GridPhysicsActor, ThrusterExhaustEmitter {

    // --- tuning -----------------------------------------------------------------------------

    /** Gas asked for at a full signal, in moles per tick: half a kilogram of Stellane lasts 50 s. */
    public static final double MAX_MOL_PER_TICK = 0.25;
    /** Thrust at a full signal on pure Stellane, in {@link GridPhysics} units: a plain block weighs 1. */
    public static final double MAX_THRUST = 400;
    public static final double THRUST_PER_MOL = MAX_THRUST / MAX_MOL_PER_TICK;
    /** How hard a mole of unburnt gas pushes, against a mole of burning Stellane. */
    public static final double COLD_PUSH_RATIO = 0.1;
    /** Temperature the Exhaust leaves at, in kelvin. */
    public static final double BURN_TEMPERATURE_K = 1200;

    /*
     * Steering, by how far the thrust points off the line through the centre of mass, in degrees, 0 to 90 either
     * way along it. Up to PUSH_ONLY it only pushes, as if through the centre of mass; from TURN_ONLY it only turns
     * the grid about it. Between FULL_TURN and FULL_PUSH it does both in full, as a force at the nozzle would, and
     * either side of that each fades out smoothly into its safe zone.
     */
    public static final double PUSH_ONLY_DEGREES = 20;
    public static final double FULL_TURN_DEGREES = 35;
    public static final double FULL_PUSH_DEGREES = 55;
    public static final double TURN_ONLY_DEGREES = 70;

    /** Below this many kilograms a share is not worth taking. */
    private static final double MIN_TRANSFER = 1e-9;
    /** Steps the burn and its cold share are sent to clients in. */
    private static final int SYNC_STEPS = 32;

    private static final String BURN_KEY = "Burn";
    private static final String COLD_KEY = "Cold";

    /** Thrust for the physics step, in the grid's own axes; zero while not burning. */
    private volatile Vec3 thrust = Vec3.ZERO;

    /** Gas drawn last tick against {@link #MAX_MOL_PER_TICK}, and the unburnt share of it, in {@link #SYNC_STEPS}. */
    private int burnSteps;
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

        burnFuel();
        syncNodeState();
    }

    private void burnFuel() {
        BlockState state = getBlockState();
        double requested = state.getValue(ThrusterBlock.POWER) / 15.0 * MAX_MOL_PER_TICK;
        Direction facing = state.getValue(ThrusterBlock.FACING);

        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos node = getDuctNodePosition();
        Map<GasType, Double> gases = Map.copyOf(getGases());
        double available = 0;
        for (Map.Entry<GasType, Double> entry : gases.entrySet()) {
            available += entry.getKey().massToMoles(entry.getValue());
        }

        double burnt = 0;
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
                    burnt += gas.massToMoles(take);
                    exhaustKg += take;
                } else {
                    unburnt += gas.massToMoles(take);
                    passed.merge(gas, take, Double::sum);
                }
            }
        }

        double force = (burnt + COLD_PUSH_RATIO * unburnt) * THRUST_PER_MOL;
        thrust = force > 0 ? Vec3.atLowerCornerOf(facing.getNormal()).scale(-force) : Vec3.ZERO;

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

        double drawn = burnt + unburnt;
        setSynced((int) Math.round(Math.min(1.0, drawn / MAX_MOL_PER_TICK) * SYNC_STEPS),
                drawn > 0 ? (int) Math.round(unburnt / drawn * SYNC_STEPS) : 0);
    }

    private void setSynced(int burn, int cold) {
        if (burn == burnSteps && cold == coldSteps) {
            return;
        }
        burnSteps = burn;
        coldSteps = cold;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Gas drawn last tick, 0 to 1 of a full signal's worth: the throttle the feed actually allowed. */
    public float burn() {
        return burnSteps / (float) SYNC_STEPS;
    }

    /** The unburnt share of what was drawn last tick, by moles. */
    public float coldFraction() {
        return coldSteps / (float) SYNC_STEPS;
    }

    /** The thrust handed to the physics step, in the grid's own axes. */
    public Vec3 thrust() {
        return thrust;
    }

    // --- physics ----------------------------------------------------------------------------

    /**
     * Pushes and turns the grid as {@link #steer} reads the thruster: through the centre of mass when lined up with
     * it, about it when square across it.
     */
    @Override
    public void onGridPhysicsTick(GridPhysics physics) {
        Vec3 force = thrust;
        if (force.lengthSqr() == 0) {
            return;
        }
        Vec3 centreOfMass = physics.centreOfMass();
        Steering steering = steer(Vec3.atCenterOf(worldPosition).subtract(centreOfMass), force);
        if (steering.push().lengthSqr() > 0) {
            physics.applyForce(centreOfMass, steering.push());
        }
        if (steering.turn().lengthSqr() > 0) {
            physics.applyTorque(steering.turn());
        }
    }

    /** What a thrust does to its grid: a force through the centre of mass, and a torque about it. */
    public record Steering(Vec3 push, Vec3 turn) {}

    /**
     * Guesses whether a thruster is there to drive the grid or to turn it, from how its thrust lines up with the
     * centre of mass, and keeps only that. A force at the nozzle is the whole force through the centre of mass
     * plus the torque {@code lever x force}; each is weighted by the angle, see {@link #PUSH_ONLY_DEGREES}.
     *
     * @param lever from the centre of mass to where the thrust acts
     * @param force the thrust
     */
    public static Steering steer(Vec3 lever, Vec3 force) {
        double leverLength = lever.length();
        double forceLength = force.length();
        if (forceLength == 0) {
            return new Steering(Vec3.ZERO, Vec3.ZERO);
        }
        // Folded so pushing towards the centre of mass reads the same as pushing away from it. A thruster on the
        // centre of mass has no lever to turn with, so it counts as lined up.
        double alignment = leverLength < 1e-6 ? 1 : Math.min(1, Math.abs(lever.dot(force)) / (leverLength * forceLength));
        double degrees = Math.toDegrees(Math.acos(alignment));
        double push = 1 - smoothstep(FULL_PUSH_DEGREES, TURN_ONLY_DEGREES, degrees);
        double turn = smoothstep(PUSH_ONLY_DEGREES, FULL_TURN_DEGREES, degrees);
        return new Steering(force.scale(push), lever.cross(force).scale(turn));
    }

    /** 0 up to {@code from}, 1 from {@code to}, and an S-curve between with no kink at either end. */
    private static double smoothstep(double from, double to, double x) {
        double t = Math.clamp((x - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
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
                coldFraction()
        );
    }

    // --- client sync ------------------------------------------------------------------------

    /** Only the burn goes to clients; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte(BURN_KEY, (byte) burnSteps);
        tag.putByte(COLD_KEY, (byte) coldSteps);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        burnSteps = tag.getByte(BURN_KEY);
        coldSteps = tag.getByte(COLD_KEY);
    }
}
