package g_mungus.zps.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A "rolling" recipe: a single input item is transformed into a single result over
 * {@link #processTime} ticks while the Rolling Mill drains {@link #energyPerTick} FE each tick.
 * Used for ingot -> rod and nugget -> wire transforms.
 */
public class RollingRecipe implements Recipe<SingleRecipeInput> {
    private final Ingredient ingredient;
    private final ItemStack result;
    private final int processTime;
    private final int energyPerTick;

    public RollingRecipe(Ingredient ingredient, ItemStack result, int processTime, int energyPerTick) {
        this.ingredient = ingredient;
        this.result = result;
        this.processTime = processTime;
        this.energyPerTick = energyPerTick;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack result() {
        return result;
    }

    public int processTime() {
        return processTime;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    @Override
    public boolean matches(SingleRecipeInput input, @NotNull Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull SingleRecipeInput input, HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return result;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.ROLLING_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.ROLLING_TYPE.get();
    }
}
