package g_mungus.zps.blockentity.gas;

import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.ThrusterFlame;
import g_mungus.zps.recipe.ThrusterFuelInput;
import g_mungus.zps.recipe.ThrusterFuelRecipe;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * What a thruster does with one gas: from its {@link g_mungus.zps.recipe.ThrusterFuelRecipe} if it has one, or else
 * {@link #unburnt}.
 *
 * @param thrust              the push of a full signal on nothing but this gas, against that of Stellane
 * @param fullDrawKgPerSecond how much of it a full signal draws, in kilograms per second
 * @param result              the gas it leaves as, the same mass as went in; null to leave as itself
 * @param temperatureK        what it leaves at, in kelvin; NaN to leave at the temperature it came in at
 * @param flame               how it shows in the plume
 */
public record ThrusterPropellant(double thrust, double fullDrawKgPerSecond, @Nullable GasType result,
                                 double temperatureK, ThrusterFlame flame) {

    private static final int TICKS_PER_SECOND = 20;

    /**
     * What a thruster does with {@code gas} when it has no recipe: draws {@link ThrusterBlockEntity#UNBURNT_MOL_PER_TICK}
     * of it and lets it out as it came, as fast as air, so it pushes by its mass against air's.
     */
    public static ThrusterPropellant unburnt(GasType gas) {
        GasType air = GasTypeRegistry.INSTANCE.getGasType(AtmosphericComposition.AIR);
        double againstAir = air != null ? gas.getDensity() / air.getDensity() : 1;
        return new ThrusterPropellant(ThrusterBlockEntity.UNBURNT_THRUST * againstAir,
                gas.molesToMass(ThrusterBlockEntity.UNBURNT_MOL_PER_TICK) * TICKS_PER_SECOND, null, Double.NaN,
                ThrusterFlame.COLD);
    }

    /** What a thruster does with {@code gas}, by the recipes {@code level} has loaded. */
    public static ThrusterPropellant of(Level level, GasType gas) {
        ThrusterPropellant propellant = level.getRecipeManager()
                .getRecipeFor(ModRecipes.THRUSTER_FUEL_TYPE.get(), new ThrusterFuelInput(gas), level)
                .map(ThrusterFuelRecipe::propellant)
                .orElse(null);
        return propellant != null ? propellant : unburnt(gas);
    }

    /** Whether it passes through as itself, from having no recipe. */
    public boolean isUnburnt() {
        return result == null;
    }

    /** Kilograms a full signal draws each tick. */
    public double fullDrawKgPerTick() {
        return fullDrawKgPerSecond / TICKS_PER_SECOND;
    }

    /** The thrust a kilogram of it gives as it goes through. */
    public double pushPerKg() {
        return fullThrust() / fullDrawKgPerTick();
    }

    /** The thrust a full signal gives on a feed of nothing but this gas. */
    public double fullThrust() {
        return thrust * ThrusterBlockEntity.maxThrust();
    }
}
