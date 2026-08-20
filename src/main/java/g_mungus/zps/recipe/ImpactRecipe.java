package g_mungus.zps.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An "impact" recipe: the Impact Piston slams its rod into the block below and, if that block is in
 * {@link #ingredient}, replaces it with one of {@link #results} chosen by weight.
 *
 * <p>Unlike the mod's other recipe types this one operates on blocks in the world rather than on
 * items in slots, so the item-oriented half of {@link Recipe} is inert. Stroke time and power draw
 * are fixed properties of the machine, not of the recipe. Used for brick -> cracked brick and
 * cobblestone -> gravel transforms.
 */
public class ImpactRecipe implements Recipe<ImpactInput> {
    private final HolderSet<Block> ingredient;
    private final List<ImpactResult> results;

    public ImpactRecipe(HolderSet<Block> ingredient, List<ImpactResult> results) {
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
    }

    public HolderSet<Block> ingredient() {
        return ingredient;
    }

    public List<ImpactResult> results() {
        return results;
    }

    @Override
    public boolean matches(ImpactInput input, @NotNull Level level) {
        return ingredient.contains(input.state().getBlockHolder());
    }

    /** Picks one outcome in proportion to the entry weights. */
    public ImpactResult pick(RandomSource random) {
        int total = 0;
        for (ImpactResult result : results) {
            total += result.weight();
        }
        if (total <= 0) {
            return results.getFirst();
        }
        int roll = random.nextInt(total);
        for (ImpactResult result : results) {
            roll -= result.weight();
            if (roll < 0) {
                return result;
            }
        }
        return results.getLast();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull ImpactInput input, HolderLookup.@NotNull Provider registries) {
        // Impact recipes place a block rather than producing an item stack.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        // The input is a block, not an item, so there is nothing for the item-based API to report.
        return NonNullList.create();
    }

    /** The most likely outcome, shown as the recipe's representative result in JEI and tooltips. */
    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        ImpactResult best = results.getFirst();
        for (ImpactResult result : results) {
            if (result.weight() > best.weight()) {
                best = result;
            }
        }
        return new ItemStack(best.block().value());
    }

    /** Keeps these out of the vanilla recipe book, which cannot represent a block-to-block transform. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.IMPACT_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.IMPACT_TYPE.get();
    }
}
