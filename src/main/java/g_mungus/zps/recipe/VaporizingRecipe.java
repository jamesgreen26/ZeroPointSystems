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
 * <p>Items are matched by what the machine holds, not how it is stacked: the slots are pooled, each
 * ingredient takes one item from any slot that still has one to give, and every kind of item in
 * the machine must be taken from by some ingredient, so a stray item blocks the recipe while a
 * stack split across two slots does not. Each craft takes one item per ingredient and adds every
 * {@link GasOutput} to the machine's gas buffer.
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
     * Which slot each ingredient takes one item from: {@code result[i]} is the slot satisfying
     * ingredient {@code i}. A slot may serve several ingredients, one item each, as long as it
     * holds that many. Null if the input does not match — an ingredient unmet, or a kind of item
     * in the machine that no ingredient takes.
     */
    public int @Nullable [] findSlotAssignment(VaporizingInput input) {
        if (input.isEmpty()) {
            return null;
        }
        int[] remaining = new int[input.size()];
        for (int slot = 0; slot < input.size(); slot++) {
            remaining[slot] = input.getItem(slot).getCount();
        }
        int[] assignment = new int[ingredients.size()];
        return assign(input, 0, remaining, assignment) ? assignment : null;
    }

    private boolean assign(VaporizingInput input, int ingredientIndex, int[] remaining, int[] assignment) {
        if (ingredientIndex == ingredients.size()) {
            return everyKindIsUsed(input, remaining);
        }
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (remaining[slot] <= 0 || stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            remaining[slot]--;
            assignment[ingredientIndex] = slot;
            if (assign(input, ingredientIndex + 1, remaining, assignment)) {
                return true;
            }
            remaining[slot]++;
        }
        return false;
    }

    /**
     * Whether every kind of item in the input has had at least one taken from some slot holding
     * it. A kind is an item with its components, the way stacks decide whether they merge.
     */
    private static boolean everyKindIsUsed(VaporizingInput input, int[] remaining) {
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty() || remaining[slot] < stack.getCount()) {
                continue;
            }
            // Nothing taken from this slot: fine only if the same kind was taken from another.
            boolean takenElsewhere = false;
            for (int other = 0; other < input.size(); other++) {
                ItemStack otherStack = input.getItem(other);
                if (other != slot && remaining[other] < otherStack.getCount()
                        && ItemStack.isSameItemSameComponents(stack, otherStack)) {
                    takenElsewhere = true;
                    break;
                }
            }
            if (!takenElsewhere) {
                return false;
            }
        }
        return true;
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
