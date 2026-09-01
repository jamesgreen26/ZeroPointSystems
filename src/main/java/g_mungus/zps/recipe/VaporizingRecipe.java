package g_mungus.zps.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.List;

/**
 * Items in, gas out: the Vaporizer consumes one of each ingredient over {@link #processTime} ticks,
 * draining {@link #energyPerTick} FE, and adds {@link #amount} kilograms of {@link #gas} to its own
 * buffer at {@link #temperature} Kelvin.
 *
 * <p>There is no item result — the product is a gas, so {@link #getResultItem} is always empty.
 */
public class VaporizingRecipe implements Recipe<VaporizingInput> {

    private final List<Ingredient> ingredients;
    private final ResourceLocation gas;
    private final double amount;
    private final double temperature;
    private final int processTime;
    private final int energyPerTick;

    public VaporizingRecipe(List<Ingredient> ingredients, ResourceLocation gas, double amount,
                            double temperature, int processTime, int energyPerTick) {
        this.ingredients = List.copyOf(ingredients);
        this.gas = gas;
        this.amount = amount;
        this.temperature = temperature;
        this.processTime = processTime;
        this.energyPerTick = energyPerTick;
    }

    public List<Ingredient> ingredientList() {
        return ingredients;
    }

    public ResourceLocation gas() {
        return gas;
    }

    /** Kilograms of gas produced per batch. */
    public double amount() {
        return amount;
    }

    /** Temperature the gas enters the network at, in Kelvin. */
    public double temperature() {
        return temperature;
    }

    public int processTime() {
        return processTime;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    /** Resolved against Kelvin's registry, which is populated during mod construction. */
    public GasType gasType() {
        return GasTypeRegistry.INSTANCE.getGasType(gas);
    }

    /**
     * Every ingredient must be satisfied by a different slot. With only two slots the pairing is
     * checked directly, so a recipe does not care which slot a player drops things into.
     */
    @Override
    public boolean matches(VaporizingInput input, @NotNull Level level) {
        if (ingredients.isEmpty() || ingredients.size() > input.size()) {
            return false;
        }
        if (ingredients.size() == 1) {
            return ingredients.getFirst().test(input.primary()) || ingredients.getFirst().test(input.secondary());
        }

        Ingredient first = ingredients.get(0);
        Ingredient second = ingredients.get(1);
        return (first.test(input.primary()) && second.test(input.secondary()))
                || (first.test(input.secondary()) && second.test(input.primary()));
    }

    /** Which slot satisfies the given ingredient, or -1. Used to consume the right stacks. */
    public int slotFor(VaporizingInput input, int ingredientIndex, int alreadyUsedSlot) {
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            if (slot != alreadyUsedSlot && ingredient.test(input.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull VaporizingInput input, HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.createWithCapacity(ingredients.size());
        list.addAll(ingredients);
        return list;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        // No item result, so it must never appear in the vanilla recipe book.
        return true;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.VAPORIZING_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.VAPORIZING_TYPE.get();
    }
}
