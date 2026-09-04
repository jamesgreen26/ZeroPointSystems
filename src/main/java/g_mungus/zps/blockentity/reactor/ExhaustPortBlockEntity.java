package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.reactor.ReactorGasWallBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.Map;

/**
 * Draws ash out of the chamber. Each tick it moves up to a fixed mass of everything except Flux
 * from the chamber into its own stub, cooled to a temperature a duct can carry, and stops once
 * the stub backs up. The heat it strips off is simply lost: the Heat Exchangers are the only
 * things that turn chamber heat into anything useful.
 */
public class ExhaustPortBlockEntity extends GasNodeBlockEntity {

    private static final double MIN_TRANSFER = 1e-9;

    /** Gas drawn per tick, averaged over a sync window, so clients see a rate. */
    private double drawnSinceSync;
    private double totalDrawn;

    public ExhaustPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXHAUST_PORT.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        draw(serverLevel);
        syncNodeState();
    }

    /** The reactor this port drains, or null if it is not facing into one. */
    public Reactor reactor(ServerLevel serverLevel) {
        return ReactorManager.get(serverLevel).reactorServedBy(worldPosition, ReactorGasWallBlock.facing(getBlockState()));
    }

    private void draw(ServerLevel serverLevel) {
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return;
        }
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos own = getDuctNodePosition();
        DuctNodePos host = reactor.hostNodePos(serverLevel);
        if (kelvin.getNodeAt(own) == null || !(kelvin.getNodeAt(host) instanceof ReactorChamberNode)) {
            return;
        }
        // Gated on the stub's own pressure, not the chamber's: a well-run chamber holds a few
        // grams at a few kilopascals, less than the stub, and would never drain otherwise.
        if (getPressure() >= ZPSConfig.exhaustBackpressureLimitPa()) {
            return;
        }

        double budget = ZPSConfig.exhaustKgPerTick();
        double outletTemperature = Math.min(kelvin.getTemperatureAt(host), ZPSConfig.exhaustOutletTemperatureK());

        for (Map.Entry<GasType, Double> entry : Map.copyOf(kelvin.getGasMassAt(host)).entrySet()) {
            if (budget <= MIN_TRANSFER) {
                break;
            }
            GasType gas = entry.getKey();
            if (gas == ModGases.FLUX) {
                continue;
            }
            double take = Math.min(entry.getValue(), budget);
            if (take <= MIN_TRANSFER) {
                continue;
            }
            if (kelvin.removeGas(host, gas, take)) {
                kelvin.addGasAtTemperature(own, gas, take, outletTemperature);
                budget -= take;
                drawnSinceSync += take;
                totalDrawn += take;
            }
        }

        // Whatever arrives, the stub never runs hotter than its outlet rating.
        double temperature = getTemperature();
        double limit = ZPSConfig.exhaustOutletTemperatureK();
        if (temperature > limit) {
            kelvin.modHeatEnergy(own, -(temperature - limit) * kelvin.getNodeHeatCapacity(own));
        }
    }

    @Override
    protected double massForSync() {
        return drawnSinceSync / syncInterval();
    }

    @Override
    protected boolean syncNodeState() {
        if (!super.syncNodeState()) {
            return false;
        }
        drawnSinceSync = 0;
        return true;
    }

    /** Everything this port has ever drawn out of a chamber, in kilograms. */
    public double getTotalDrawn() {
        return totalDrawn;
    }
}
