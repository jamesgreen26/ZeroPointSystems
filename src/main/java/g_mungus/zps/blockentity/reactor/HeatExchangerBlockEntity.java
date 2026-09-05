package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.reactor.HeatExchangerBlock;
import g_mungus.zps.blockentity.EnergyGeneratorBE;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.util.TickAverage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * Chamber heat to FE and back. 1 FE is 1 kJ.
 *
 * <p>There is no buffer: FE pushed in becomes chamber heat on the spot, and FE pulled out comes
 * straight from chamber heat. Each tick the exchanger also offers what the chamber can spare to
 * whatever is on its outer face. Both directions are capped per tick.
 *
 * <p>Two temperature limits keep the reactor alive. Nothing is drawn below a floor a little above
 * ignition, so the exchangers never pull a running chamber down to where one cold dose of fuel
 * quenches it. Nothing is accepted above a cutoff, so a big power source cannot cook the chamber
 * past melting on its own.
 */
public class HeatExchangerBlockEntity extends BlockEntity implements EnergyGeneratorBE {

    private static final double JOULES_PER_FE = 1000.0;

    private final IEnergyStorage energyStorage = new ChamberEnergyStorage();

    // FE moved so far this tick, against the per-tick caps, and the finished figures for last tick.
    private int inThisTick;
    private int outThisTick;
    private int inLastTick;
    private int outLastTick;

    private final TickAverage outAverage = new TickAverage(HUD_AVERAGE_WINDOW_TICKS);
    private int hudInfo;
    private long lastHudInfoRequestTick = Long.MIN_VALUE;

    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAT_EXCHANGER.get(), pos, state);
    }

    // --- capability ---------------------------------------------------------------------------

    /** Only the outer face carries FE. */
    public @Nullable IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return side == null || side == facing() ? energyStorage : null;
    }

    private Direction facing() {
        return getBlockState().getValue(HeatExchangerBlock.FACING);
    }

    /** True while FE was coming in last tick rather than going out. */
    public boolean isHeating() {
        return inLastTick > 0;
    }

    /** The reactor this exchanger serves, or null if it is not facing into one. */
    public @Nullable Reactor reactor(ServerLevel serverLevel) {
        return ReactorManager.get(serverLevel).reactorServedBy(worldPosition, facing());
    }

    /** The chamber this exchanger touches, or null if there is none to touch. */
    private @Nullable Chamber chamber() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return null;
        }
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos host = reactor.hostNodePos(serverLevel);
        return kelvin.getNodeAt(host) instanceof ReactorChamberNode ? new Chamber(reactor, kelvin, host) : null;
    }

    private record Chamber(Reactor reactor, DuctNetwork<?> kelvin, DuctNodePos host) {
        double temperature() {
            return kelvin.getTemperatureAt(host);
        }

        double heatCapacity() {
            return kelvin.getNodeHeatCapacity(host);
        }

        void addHeat(double joules) {
            kelvin.modHeatEnergy(host, joules);
        }
    }

    // --- work ---------------------------------------------------------------------------------

    public void serverTick() {
        inLastTick = inThisTick;
        outLastTick = outThisTick;
        inThisTick = 0;
        outThisTick = 0;
        pushEnergy();
        if (level != null) {
            outAverage.set(outLastTick, level.getGameTime());
        }
    }

    /** Offer what the chamber can spare to whatever is on the outer face. */
    private void pushEnergy() {
        if (level == null) {
            return;
        }
        Direction side = facing();
        BlockPos targetPos = worldPosition.relative(side);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) {
            return;
        }
        IEnergyStorage targetEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                targetPos, target.getBlockState(), target, side.getOpposite());
        if (targetEnergy == null || !targetEnergy.canReceive()) {
            return;
        }

        int available = energyStorage.extractEnergy(Integer.MAX_VALUE, true);
        int accepted = targetEnergy.receiveEnergy(available, true);
        int transfer = Math.min(available, accepted);
        if (transfer <= 0) {
            return;
        }
        int extracted = energyStorage.extractEnergy(transfer, false);
        int received = targetEnergy.receiveEnergy(extracted, false);
        if (received < extracted) {
            // Put back what the neighbour would not take after all.
            Chamber chamber = chamber();
            if (chamber != null) {
                chamber.addHeat((extracted - received) * JOULES_PER_FE);
                chamber.reactor().recordFeOut(received - extracted);
            }
            outThisTick -= extracted - received;
        }
    }

    // --- HUD ----------------------------------------------------------------------------------

    @Override
    public void setLastHudRefreshTick(long ticks) {
        lastHudInfoRequestTick = ticks;
    }

    @Override
    public long getLastHudRefreshTick() {
        return lastHudInfoRequestTick;
    }

    @Override
    public void provideInfo(Integer info) {
        hudInfo = info;
    }

    /** Average FE per tick leaving through the outer face over the last {@value #HUD_AVERAGE_WINDOW_TICKS} ticks. */
    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide()) {
            return outAverage.average(level.getGameTime());
        }
        return hudInfo;
    }

    // --- storage ------------------------------------------------------------------------------

    /**
     * The chamber's heat, seen as an energy storage from the outer face. Nothing is stored here;
     * every call goes straight through to Kelvin.
     */
    private class ChamberEnergyStorage implements IEnergyStorage {

        /** FE the chamber could give up right now, within this tick's cap. */
        private int spare(Chamber chamber) {
            double surplus = (chamber.temperature() - ZPSConfig.exchangerGenerationFloorK()) * chamber.heatCapacity();
            int cap = ZPSConfig.exchangerFePerTick() - outThisTick;
            return (int) Math.max(0, Math.min(cap, surplus / JOULES_PER_FE));
        }

        /** FE the chamber could take right now, within this tick's cap. */
        private int room(Chamber chamber) {
            if (chamber.temperature() >= ZPSConfig.exchangerHeatingCutoffK()) {
                return 0;
            }
            return Math.max(0, ZPSConfig.exchangerFePerTick() - inThisTick);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            Chamber chamber = chamber();
            if (chamber == null || toReceive <= 0) {
                return 0;
            }
            int fe = Math.min(toReceive, room(chamber));
            if (fe > 0 && !simulate) {
                chamber.addHeat(fe * JOULES_PER_FE);
                chamber.reactor().recordFeIn(fe);
                inThisTick += fe;
            }
            return fe;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            Chamber chamber = chamber();
            if (chamber == null || toExtract <= 0) {
                return 0;
            }
            int fe = Math.min(toExtract, spare(chamber));
            if (fe > 0 && !simulate) {
                chamber.addHeat(-fe * JOULES_PER_FE);
                chamber.reactor().recordFeOut(fe);
                outThisTick += fe;
            }
            return fe;
        }

        /** What could be pulled right now; there is nothing actually held. */
        @Override
        public int getEnergyStored() {
            Chamber chamber = chamber();
            return chamber == null ? 0 : spare(chamber);
        }

        @Override
        public int getMaxEnergyStored() {
            return ZPSConfig.exchangerFePerTick();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
