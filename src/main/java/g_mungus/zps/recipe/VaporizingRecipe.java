package g_mungus.zps.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A "vaporizing" recipe: a shapeless set of items is turned into gas by the Vaporizer.
 *
 * <p>Items are matched the way a shapeless crafting recipe matches them — every ingredient must be
 * satisfied by a distinct slot, and every occupied slot must be used. Each craft takes one item from
 * each of those slots and adds every {@link GasOutput} to the machine's gas buffer.
 *
 * <p>The machine must be at or above {@link #minTemperature} to run the recipe; the gas comes out at
 * whatever temperature the machine is actually at, and the machine then cools by
 * {@link #temperatureCost}.
 */
public class VaporizingRecipe implements Recipe<VaporizingInput> {
    private final List<Ingredient> ingredients;
    private final List<GasOutput> results;
    private final double minTemperature;
    private final double temperatureCost;

    public VaporizingRecipe(List<Ingredient> ingredients, List<GasOutput> results,
                            double minTemperature, double temperatureCost) {
        this.ingredients = List.copyOf(ingredients);
        this.results = List.copyOf(results);
        this.minTemperature = minTemperature;
        this.temperatureCost = temperatureCost;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public List<GasOutput> results() {
        return results;
    }

    /** The machine temperature, in Kelvin, below which this recipe will not run. */
    public double minTemperature() {
        return minTemperature;
    }

    /** How far, in Kelvin, the machine cools for each craft. */
    public double temperatureCost() {
        return temperatureCost;
    }

    /** Whether any ingredient of this recipe accepts the stack — used to gate what the slots take. */
    public boolean accepts(ItemStack stack) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(VaporizingInput input, @NotNull Level level) {
        return findSlotAssignment(input) != null;
    }

    /**
     * Which slot each ingredient takes from: {@code result[i]} is the slot satisfying ingredient
     * {@code i}. Null if the input does not match — a slot left over, an ingredient unmet, or two
     * ingredients competing for the same slot.
     */
    public int @Nullable [] findSlotAssignment(VaporizingInput input) {
        int occupied = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            if (!input.getItem(slot).isEmpty()) {
                occupied++;
            }
        }
        if (occupied != ingredients.size()) {
            return null;
        }
        int[] assignment = new int[ingredients.size()];
        return assign(input, 0, new boolean[input.size()], assignment) ? assignment : null;
    }

    private boolean assign(VaporizingInput input, int ingredientIndex, boolean[] used, int[] assignment) {
        if (ingredientIndex == ingredients.size()) {
            return true;
        }
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (used[slot] || stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            used[slot] = true;
            assignment[ingredientIndex] = slot;
            if (assign(input, ingredientIndex + 1, used, assignment)) {
                return true;
            }
            used[slot] = false;
        }
        return false;
    }

    /** The product is gas, not an item. */
    @Override
    public @NotNull ItemStack assemble(@NotNull VaporizingInput input, HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
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
        return ModRecipes.VAPORIZING_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.VAPORIZING_TYPE.get();
    }
}
