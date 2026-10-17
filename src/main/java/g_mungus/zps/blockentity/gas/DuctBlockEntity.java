package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.LeakyPipeDuctNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.GasJet;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DuctBlockEntity extends GasNodeBlockEntity {

    /** Kilograms of escaping gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.01;
    /** A leak is a split pipe, not a nozzle: a wider, lazier plume than the vent's jet. */
    private static final double JET_RADIUS = 0.2;
    private static final double JET_OFFSET = 0.45;

    public DuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUCT.get(), pos, state);
    }

    /** Only ticks while a face is open — see {@code DuctBlock.getTicker}. */
    public void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            spawnLeakPlumes();
            return;
        }
        leakIntoRooms();
        syncNodeState();
    }

    /**
     * Hand what escapes this tick to the sealed room past each open face, if there is one. Kelvin
     * takes the gas out itself, the same fraction {@link #massForSync()} reports, so this only says
     * where it went.
     */
    private void leakIntoRooms() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = getBlockState();
        int leaking = LeakyPipeDuctNode.countLeakingFaces(state);
        if (leaking <= 0 || !(state.getBlock() instanceof LeakyPipeDuctNode.DuctBlockFaces faces)) {
            return;
        }
        double perFace = LeakyPipeDuctNode.leakRatioFor(state) / leaking;
        Map<GasType, Double> escaping = new HashMap<>();
        getGases().forEach((gas, mass) -> escaping.put(gas, mass * perFace));
        if (escaping.isEmpty()) {
            return;
        }
        double temperatureK = getTemperature();
        RoomManager rooms = RoomManager.get(serverLevel);
        for (Direction direction : Direction.values()) {
            if (faces.connectionAt(state, direction) == DuctConnectionType.LEAK) {
                rooms.addGas(serverLevel, worldPosition.relative(direction), escaping, temperatureK);
            }
        }
    }

    /**
     * Gas escaping per tick, which is what the plume is drawn from.
     *
     * <p>Kelvin's solver does the removal itself through {@code ILeakNode}, taking this fraction of
     * whatever is at the node, so the rate can be derived rather than measured.
     */
    @Override
    protected double massForSync() {
        if (level == null || level.isClientSide()) {
            return super.massForSync();
        }
        return getGasMass() * LeakyPipeDuctNode.leakRatioFor(getBlockState());
    }

    /** How the escaping gas floats in what is past each open face. */
    @Override
    protected float[] buoyancyForSync() {
        BlockState state = getBlockState();
        if (!(level instanceof ServerLevel serverLevel)
                || !(state.getBlock() instanceof LeakyPipeDuctNode.DuctBlockFaces faces)) {
            return NO_BUOYANCY;
        }
        Map<ResourceLocation, Double> gases = new HashMap<>();
        getGases().forEach((gas, mass) -> gases.merge(gas.getResourceLocation(), mass, Double::sum));
        double temperatureK = getTemperature();
        RoomManager rooms = RoomManager.get(serverLevel);
        float[] buoyancy = new float[Direction.values().length];
        Arrays.fill(buoyancy, Float.NaN);
        for (Direction direction : Direction.values()) {
            if (faces.connectionAt(state, direction) == DuctConnectionType.LEAK) {
                buoyancy[direction.ordinal()] = (float) rooms.buoyancy(serverLevel, worldPosition.relative(direction),
                        gases, temperatureK);
            }
        }
        return buoyancy;
    }

    private void spawnLeakPlumes() {
        // On the client getGasMass() is the synced figure, which for a duct is its leak rate.
        double rate = getGasMass();
        if (rate <= 0 || level == null) {
            return;
        }

        BlockState state = getBlockState();
        int leaking = LeakyPipeDuctNode.countLeakingFaces(state);
        if (leaking <= 0) {
            return;
        }

        // The escaping gas is shared between however many holes there are.
        double perFace = rate / leaking / MASS_PER_PARTICLE;
        double speed = Mth.clamp(0.0004 * Math.pow(getPressure(), 0.4), 0.01, 0.35);
        Vec3 centre = Vec3.atCenterOf(worldPosition);

        for (Direction direction : Direction.values()) {
            if (!(state.getBlock() instanceof LeakyPipeDuctNode.DuctBlockFaces faces)
                    || faces.connectionAt(state, direction) != DuctConnectionType.LEAK) {
                continue;
            }
            GasJet.spawn(level, getDuctNodePosition(), ModGases.FLUX, centre, direction,
                    speed, perFace, JET_RADIUS, JET_OFFSET, getSyncedBuoyancy(direction));
        }
    }
}
