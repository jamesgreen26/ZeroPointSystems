package g_mungus.zps.commands.content;

import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.MechanicalCraftingCompat;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.Shaped5x5Recipe;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared logic for the {@code set_recipe} command: which crafting recipes the Assembler can fulfill, and
 * how to lay a recipe into the 5x5 pattern. Used by both {@link g_mungus.zps.commands.content.arguments
 * .AssemblerRecipeArgument} (suggestions) and the executor so the two never diverge.
 */
public final class AssemblerRecipeSupport {

    private AssemblerRecipeSupport() {
    }

    /** Ids of every recipe the Assembler can fulfill: 5x5 + vanilla crafting + (if Create is loaded) mechanical. */
    public static Set<ResourceLocation> fulfillableIds(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Shaped5x5Recipe recipe : recipeManager.getAllRecipesFor(ModRecipes.SHAPED_5X5_TYPE.get())) {
            if (isFulfillable(recipe)) {
                ids.add(recipe.getId());
            }
        }
        for (CraftingRecipe recipe : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (isFulfillable(recipe)) {
                ids.add(recipe.getId());
            }
        }
        if (Compat.isCreateLoaded()) {
            for (CraftingRecipe recipe : MechanicalCraftingCompat.mechanicalCraftingRecipes(level)) {
                if (isFulfillable(recipe)) {
                    ids.add(recipe.getId());
                }
            }
        }
        return ids;
    }

    /** Resolves a recipe id to its recipe, but only if the Assembler can fulfill it. */
    @Nullable
    public static Recipe<?> resolveFulfillable(Level level, ResourceLocation id) {
        return level.getRecipeManager().byKey(id)
                .filter(AssemblerRecipeSupport::isFulfillable)
                .orElse(null);
    }

    /**
     * A recipe is fulfillable if it is a crafting recipe (vanilla, or this mod's 5x5) with at least one real
     * ingredient — this excludes special/dynamic recipes such as map cloning, which have no fixed
     * ingredients — and fits the 5x5 grid. Create's {@code MechanicalCraftingRecipe} is a
     * {@link ShapedRecipe}, so it is handled by the shaped branch through the vanilla API. Note
     * {@code isSpecial()} is unreliable here — mechanical recipes (and {@link Shaped5x5Recipe}) report
     * {@code true} despite having a fixed pattern.
     */
    public static boolean isFulfillable(Recipe<?> recipe) {
        if (!(recipe instanceof CraftingRecipe) && !(recipe instanceof Shaped5x5Recipe)) {
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.stream().allMatch(Ingredient::isEmpty)) {
            return false;
        }
        if (isShaped(recipe)) {
            int[] size = dimensions(recipe);
            return size[0] <= AssemblerBlockEntity.GRID_WIDTH && size[1] <= AssemblerBlockEntity.GRID_HEIGHT;
        }
        return ingredients.size() <= AssemblerBlockEntity.PATTERN_SLOTS;
    }

    /** Whether the recipe carries its own pattern layout (rather than being packed like a shapeless one). */
    public static boolean isShaped(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe || recipe instanceof Shaped5x5Recipe;
    }

    /**
     * The bounding box {@code {width, height}} a recipe occupies in the pattern grid. Shaped recipes
     * (vanilla, Create mechanical, and {@code zps:shaped_5x5}) carry their own; shapeless ones are packed
     * row-major into a crafting-grid-sized (up to 3 wide) box.
     */
    public static int[] dimensions(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return new int[]{shaped.getWidth(), shaped.getHeight()};
        }
        if (recipe instanceof Shaped5x5Recipe shaped) {
            return new int[]{shaped.getWidth(), shaped.getHeight()};
        }
        int count = recipe.getIngredients().size();
        int width = Math.max(1, Math.min(count, 3));
        return new int[]{width, (count + width - 1) / width};
    }

    /**
     * Lays a fulfillable recipe into a 25-cell row-major 5x5 grid (top-left aligned). Shaped recipes keep
     * their layout; shapeless recipes fill sequentially. Returns {@code null} if the recipe is not
     * fulfillable.
     */
    @Nullable
    public static List<Ingredient> toGrid25(Recipe<?> recipe) {
        if (!isFulfillable(recipe)) {
            return null;
        }
        List<Ingredient> grid = new ArrayList<>(AssemblerBlockEntity.PATTERN_SLOTS);
        for (int i = 0; i < AssemblerBlockEntity.PATTERN_SLOTS; i++) {
            grid.add(Ingredient.EMPTY);
        }
        if (isShaped(recipe)) {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            int[] size = dimensions(recipe);
            int width = size[0];
            int height = size[1];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    grid.set(col + row * AssemblerBlockEntity.GRID_WIDTH, ingredients.get(col + row * width));
                }
            }
        } else {
            int cell = 0;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (cell >= AssemblerBlockEntity.PATTERN_SLOTS) {
                    break;
                }
                grid.set(cell++, ingredient);
            }
        }
        return grid;
    }
}
