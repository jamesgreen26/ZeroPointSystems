package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;

import g_mungus.zps.gas.GasJet;
import g_mungus.zps.gas.ModGases;

import java.util.Map;

/**
 * Empties its node into the world every tick and draws the jet that comes out, unless the block is
 * shut — see {@link VentBlock#POWERED}.
 *
 * <p>Ported from Clockwork's {@code ExhaustBlockEntity} (Apache-2.0). The venting and the
 * pressure-to-speed curve are kept; its {@code AirCurrent} half — which made the vent behave as
 * a Create fan — is dropped, since ZPS does not build on Create.
 */
public class VentBlockEntity extends GasNodeBlockEntity {

    /** Kilograms of gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.01;

    /**
     * Where the plate's outer face sits along {@link VentBlock#FACING}, measured from the block
     * centre. The plate is pushed back against the inlet, so its mouth is behind the centre and the
     * offset is negative — spawning at the centre would leave the jet starting in mid-air, well
     * clear of the block it is supposed to be coming out of.
     */
    private static final double MOUTH_OFFSET = VentBlock.PLATE_THICKNESS / 16.0 - 0.5;
    /** Mouth radius: the plate is a full block face, so the jet spreads across most of one. */
    private static final double MOUTH_RADIUS = 0.4;

    /**
     * Gas vented per tick, averaged over a sync window. This — not the mass at the node — is what
     * the jet draws: the vent empties its node every single tick, so the residue is always zero
     * and a jet drawn from it would never appear.
     */
    private double ventedSinceSync;
    /** Highest pressure seen just before venting, which is what drove the gas out. */
    private double peakPressureSinceSync;
    /** Everything this vent has ever vented, in kilograms. Never reset. */
    private double totalVented;

    /**
     * How many ticks gas last seen at the node counts as still passing through. The vent empties
     * its node every tick, so at any given moment there is usually nothing there to take the
     * temperature of; what was there a moment ago is what anyone sitting in the vent feels.
     */
    private static final int GAS_MEMORY_TICKS = 10;
    /** Temperature of the gas last seen at the node, in kelvin, and the tick it was seen on. */
    private double lastGasTemperatureK;
    private long lastGasSeenTick = NEVER;
    /** Far enough back that no game time is within memory of it, without overflowing a subtraction. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    public VentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT.get(), pos, state);
    }

    public void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            spawnJet();
            return;
        }

        vent();
        syncNodeState();
    }

    /** Dump everything at this node. This is the whole point of the block. */
    private void vent() {
        // Sampled before emptying, and while shut too: a closed vent is exactly where pressure is
        // worth watching, and afterwards there is nothing left to measure.
        peakPressureSinceSync = Math.max(peakPressureSinceSync, getPressure());

        Map<GasType, Double> gases = getGases();
        if (gases.isEmpty()) {
            return;
        }
        // Taken while there is gas to take it of, shut or not: a shut vent holding hot gas is as
        // hot to sit in as an open one passing it.
        lastGasTemperatureK = getTemperature();
        lastGasSeenTick = level == null ? 0 : level.getGameTime();

        if (VentBlock.isShut(getBlockState())) {
            return;
        }

        for (Map.Entry<GasType, Double> entry : Map.copyOf(gases).entrySet()) {
            if (KelvinMod.INSTANCE.forceGetKelvin()
                    .removeGas(getDuctNodePosition(), entry.getKey(), entry.getValue())) {
                ventedSinceSync += entry.getValue();
                totalVented += entry.getValue();
            }
        }
    }

    @Override
    protected double massForSync() {
        return ventedSinceSync / syncInterval();
    }

    @Override
    protected double pressureForSync() {
        return peakPressureSinceSync;
    }

    @Override
    protected boolean syncNodeState() {
        if (!super.syncNodeState()) {
            return false;
        }
        // The window has been reported; start the next one.
        ventedSinceSync = 0;
        peakPressureSinceSync = 0;
        return true;
    }

    /** Gas vented per tick over the current window. This is what gets sent to clients. */
    public double getVentedPerTick() {
        return ventedSinceSync / syncInterval();
    }

    /** Total gas this vent has vented, in kilograms. */
    public double getTotalVented() {
        return totalVented;
    }

    /**
     * The temperature of the gas passing this vent, in kelvin: that of the gas last seen at its
     * node, for a short while after it was seen. {@link Double#NaN} once nothing has come through
     * for a while — no gas is neither hot nor cold, and NaN fails every comparison. Server side
     * only.
     */
    public double gasTemperatureK() {
        if (level == null || level.getGameTime() > lastGasSeenTick + GAS_MEMORY_TICKS) {
            return Double.NaN;
        }
        return lastGasTemperatureK;
    }

    private void spawnJet() {
        // On the client getGasMass() is the synced figure, which for a vent is its vent rate.
        double rate = getGasMass();
        if (rate <= 0 || level == null) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(DirectionalBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);

        // Pressure drives how hard the jet comes out; the curve is Clockwork's.
        double speed = Mth.clamp(0.0005 * Math.pow(getPressure(), 0.4), 0.02, 0.5);

        GasJet.spawn(level, getDuctNodePosition(), ModGases.FLUX,
                Vec3.atCenterOf(worldPosition), facing, speed,
                rate / MASS_PER_PARTICLE, MOUTH_RADIUS, MOUTH_OFFSET);
    }
}
