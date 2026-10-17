package g_mungus.zps.gas.api;

import g_mungus.zps.gas.ModGasTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What a living entity is breathing and standing in this tick. Built fresh each server tick from the
 * atmosphere at its eyes, then handed to every queued {@link GasEnvironmentModifier} and to
 * {@link GasEnvironmentEvent} listeners, and finally applied once through {@code GasExposure}: hot or
 * cold enough to hurt by its temperature, and drowning unless it is breathable.
 *
 * <p>Gases are weights, not fractions: adding one dilutes the rest. {@link #fractions()} gives them
 * normalised. The temperature is what is applied, as is — {@link Double#NaN} for nothing there to
 * carry heat or cold — so a source that replaces the gases with nothing should say so here too.
 */
public final class GasEnvironment {
    private final LivingEntity entity;
    private final BlockPos eyes;
    private double temperatureK;
    private final Map<ResourceLocation, Double> gases;
    private @Nullable Boolean breathable;
    private boolean exposed = true;

    public GasEnvironment(LivingEntity entity, BlockPos eyes, double temperatureK, Map<ResourceLocation, Double> gases) {
        this.entity = entity;
        this.eyes = eyes;
        this.temperatureK = temperatureK;
        this.gases = new HashMap<>(gases);
    }

    public LivingEntity entity() {
        return entity;
    }

    /** The block the entity's eyes are in, where the atmosphere was read. */
    public BlockPos eyes() {
        return eyes;
    }

    /** The temperature the entity is exposed to, in kelvin, or {@link Double#NaN} for none. */
    public double temperatureK() {
        return temperatureK;
    }

    public void setTemperatureK(double temperatureK) {
        this.temperatureK = temperatureK;
    }

    /** The gases present, by weight. */
    public Map<ResourceLocation, Double> gases() {
        return Collections.unmodifiableMap(gases);
    }

    /** The gases present, as fractions summing to 1; empty for nothing at all. */
    public Map<ResourceLocation, Double> fractions() {
        double total = gases.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<ResourceLocation, Double> fractions = new HashMap<>();
        if (total > 0) {
            gases.forEach((gas, weight) -> fractions.put(gas, weight / total));
        }
        return fractions;
    }

    /** Replace every gas with {@code gases}, by weight. */
    public void setGases(Map<ResourceLocation, Double> gases) {
        this.gases.clear();
        gases.forEach(this::addGas);
    }

    /** Add {@code weight} of {@code gas}, on top of any already there. */
    public void addGas(ResourceLocation gas, double weight) {
        if (weight > 0) {
            gases.merge(gas, weight, Double::sum);
        }
    }

    public void removeGas(ResourceLocation gas) {
        gases.remove(gas);
    }

    /**
     * Whether the entity can breathe here: as {@link #setBreathable} said, or else whether any gas
     * present is {@link ModGasTags#BREATHABLE}.
     */
    public boolean isBreathable() {
        if (breathable != null) {
            return breathable;
        }
        for (Map.Entry<ResourceLocation, Double> gas : gases.entrySet()) {
            if (gas.getValue() > 0 && ModGasTags.isBreathable(entity.level(), gas.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Force breathability either way, whatever the gases; null goes back to judging by the gases. */
    public void setBreathable(@Nullable Boolean breathable) {
        this.breathable = breathable;
    }

    /** Whether anything is applied at all. */
    public boolean isExposed() {
        return exposed;
    }

    /** Shield the entity from all of it this tick, temperature and drowning both. */
    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }
}
