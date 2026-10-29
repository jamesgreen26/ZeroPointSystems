package g_mungus.zps.recipe;

import g_mungus.zps.blockentity.gas.ThrusterPropellant;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * A "thruster_fuel" recipe: how a thruster burns one gas. A gas with no recipe passes through unburnt
 * ({@link ThrusterPropellant#unburnt}).
 *
 * <p>Gases are held by id, like {@link GasOutput}, since recipes may load before every mod has registered its gases;
 * a recipe whose result never turns up is ignored and the fuel falls back to passing through.
 *
 * @param fuel        the gas burnt
 * @param consumption how much of it a full signal burns, in kilograms per second
 * @param thrust      the push of a full signal on nothing but this gas, against that of Stellane
 * @param result      the gas it leaves as, the same mass as went in
 * @param temperature what the result leaves at, in kelvin
 * @param flame       how it shows in the plume
 */
public record ThrusterFuelRecipe(ResourceLocation fuel, double consumption, double thrust, ResourceLocation result,
                                 double temperature, ThrusterFlame flame) implements Recipe<ThrusterFuelInput> {

    /** What this recipe makes a thruster do, or null if its result gas is not registered. */
    public @Nullable ThrusterPropellant propellant() {
        GasType resultGas = GasTypeRegistry.INSTANCE.getGasType(result);
        if (resultGas == null) {
            return null;
        }
        return new ThrusterPropellant(thrust, consumption, resultGas, temperature, flame);
    }

    @Override
    public boolean matches(ThrusterFuelInput input, @NotNull Level level) {
        return input.gas().getResourceLocation().equals(fuel);
    }

    /** The product is gas, not an item. */
    @Override
    public @NotNull ItemStack assemble(@NotNull ThrusterFuelInput input, HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    /** No item result, so keep it out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.THRUSTER_FUEL_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.THRUSTER_FUEL_TYPE.get();
    }
}
