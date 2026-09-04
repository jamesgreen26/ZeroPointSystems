package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.reactor.HeatExchangerBlock;
import g_mungus.zps.blockentity.EnergyGeneratorBE;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * Chamber heat to FE and back. 1 FE is 1 kJ.
 *
 * <p>Mode follows the outside world: if anything pushed FE in since the last tick, the exchanger
 * is heating and pours its buffer into the chamber. Otherwise it is generating, drawing chamber
 * heat into the buffer and pushing that out of its outer face. Both are capped per tick.
 *
 * <p>Two temperature limits keep the reactor alive. Generation stops at a floor a little above
 * ignition, so the exchangers never pull a running chamber down to where one cold dose of fuel
 * quenches it. Heating stops at a cutoff, so a big power source cannot cook the chamber past
 * melting on its own.
 */
public class HeatExchangerBlockEntity extends BlockEntity implements EnergyGeneratorBE {

    private static final double JOULES_PER_FE = 1000.0;

    private final ExchangerEnergyStorage energyStorage = new ExchangerEnergyStorage();

    private boolean receivedThisTick;
    private boolean heating;
    /** The chamber is at or past the heating cutoff, so FE is refused. */
    private boolean chamberTooHot;
    private int outputLastTick;

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

    /** True while FE is being converted to heat rather than the other way round. */
    public boolean isHeating() {
        return heating;
    }

    /** The reactor this exchanger serves, or null if it is not facing into one. */
    public Reactor reactor(ServerLevel serverLevel) {
        return ReactorManager.get(serverLevel).reactorServedBy(worldPosition, facing());
    }

    // --- work ---------------------------------------------------------------------------------

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean received = receivedThisTick;
        receivedThisTick = false;
        outputLastTick = 0;

        Reactor reactor = reactor(serverLevel);
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos host = reactor == null ? null : reactor.hostNodePos(serverLevel);
        if (host == null || !(kelvin.getNodeAt(host) instanceof ReactorChamberNode)) {
            heating = false;
            chamberTooHot = false;
            return;
        }

        double temperature = kelvin.getTemperatureAt(host);
        chamberTooHot = temperature >= ZPSConfig.exchangerHeatingCutoffK();
        int perTick = ZPSConfig.exchangerFePerTick();

        if (received) {
            heating = true;
            int fe = energyStorage.drain(perTick);
            if (fe > 0) {
                kelvin.modHeatEnergy(host, fe * JOULES_PER_FE);
                reactor.recordFeIn(fe);
                setChanged();
            }
            return;
        }

        heating = false;
        double surplus = (temperature - ZPSConfig.exchangerGenerationFloorK()) * kelvin.getNodeHeatCapacity(host);
        if (surplus > 0) {
            int fe = (int) Math.min(Math.min(perTick, energyStorage.room()), surplus / JOULES_PER_FE);
            if (fe > 0) {
                kelvin.modHeatEnergy(host, -fe * JOULES_PER_FE);
                energyStorage.fill(fe);
                setChanged();
            }
        }
        outputLastTick = pushEnergy(perTick);
        if (outputLastTick > 0) {
            reactor.recordFeOut(outputLastTick);
        }
    }

    /** Offer buffered FE to whatever is on the outer face. Returns what it took. */
    private int pushEnergy(int limit) {
        if (level == null || energyStorage.getEnergyStored() <= 0) {
            return 0;
        }
        Direction side = facing();
        BlockPos targetPos = worldPosition.relative(side);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) {
            return 0;
        }
        IEnergyStorage targetEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                targetPos, target.getBlockState(), target, side.getOpposite());
        if (targetEnergy == null || !targetEnergy.canReceive()) {
            return 0;
        }

        int available = energyStorage.extractEnergy(limit, true);
        int accepted = targetEnergy.receiveEnergy(available, true);
        int transfer = Math.min(available, accepted);
        if (transfer <= 0) {
            return 0;
        }
        int extracted = energyStorage.extractEnergy(transfer, false);
        int received = targetEnergy.receiveEnergy(extracted, false);
        if (received < extracted) {
            energyStorage.fill(extracted - received);
        }
        setChanged();
        return received;
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

    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide()) {
            return outputLastTick;
        }
        return hudInfo;
    }

    // --- persistence --------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energyStorage.serializeNBT(registries));
        tag.putBoolean("Heating", heating);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            energyStorage.deserializeNBT(registries, tag.get("Energy"));
        }
        heating = tag.getBoolean("Heating");
    }

    // --- storage ------------------------------------------------------------------------------

    private class ExchangerEnergyStorage extends EnergyStorage {
        private ExchangerEnergyStorage() {
            super(ZPSConfig.exchangerBufferFe(), ZPSConfig.exchangerFePerTick(), ZPSConfig.exchangerFePerTick());
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (chamberTooHot) {
                return 0;
            }
            int received = super.receiveEnergy(toReceive, simulate);
            if (!simulate && received > 0) {
                receivedThisTick = true;
                setChanged();
            }
            return received;
        }

        int room() {
            return capacity - energy;
        }

        int drain(int amount) {
            int drained = Math.min(Math.max(0, amount), energy);
            energy -= drained;
            return drained;
        }

        void fill(int amount) {
            energy += Math.min(Math.max(0, amount), capacity - energy);
        }
    }
}
