package g_mungus.zps.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Shaped crafting on a 5x5 grid, craftable only in the Assembler. Semantics are identical to
 * {@code minecraft:crafting_shaped} — keyed pattern rows, surrounding whitespace shrunk away, mirrored
 * matching that slides the pattern around the grid, tag ingredients — but the pattern may be up to 5 wide
 * and 5 tall, which {@link ShapedRecipe}'s deserializer refuses (its {@code MAX_WIDTH}/{@code MAX_HEIGHT}
 * are global, so raising them would loosen every {@code minecraft:crafting_shaped} recipe in the game).
 *
 * <p>Deliberately <em>not</em> a {@link net.minecraft.world.item.crafting.CraftingRecipe}: mods scan for
 * that interface to decide what their own machines can automate, ignoring the recipe type. Create's
 * mixer, for one, treats any non-{@code ShapedRecipe} {@code CraftingRecipe} as shapeless and would both
 * list these in "Automated Shapeless Crafting" and craft them in a basin.
 */
public class Shaped5x5Recipe implements Recipe<CraftingContainer> {
    private final ResourceLocation id;
    private final String group;
    private final int width;
    private final int height;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;

    public Shaped5x5Recipe(ResourceLocation id, String group, int width, int height,
                           NonNullList<Ingredient> ingredients, ItemStack result) {
        this.id = id;
        this.group = group;
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.result = result;
    }

    public ItemStack result() {
        return result;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Slides the pattern over every position it fits in the grid, in both orientations — the same scan
     * {@link ShapedRecipe#matches} does, so a pattern smaller than the container still matches wherever it
     * is placed (the Assembler hands us an untrimmed 5x5 container).
     */
    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        for (int x = 0; x <= container.getWidth() - width; x++) {
            for (int y = 0; y <= container.getHeight() - height; y++) {
                if (matches(container, x, y, true) || matches(container, x, y, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Matches the pattern anchored at ({@code originX}, {@code originY}), optionally mirrored. */
    private boolean matches(CraftingContainer container, int originX, int originY, boolean mirrored) {
        for (int x = 0; x < container.getWidth(); x++) {
            for (int y = 0; y < container.getHeight(); y++) {
                int col = x - originX;
                int row = y - originY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (col >= 0 && row >= 0 && col < width && row < height) {
                    ingredient = mirrored
                            ? ingredients.get(width - col - 1 + row * width)
                            : ingredients.get(col + row * width);
                }
                if (!ingredient.test(container.getItem(x + y * container.getWidth()))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.width && height >= this.height;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull RegistryAccess registries) {
        return result;
    }

    @Override
    public @NotNull String getGroup() {
        return group;
    }

    /**
     * Keeps these out of the vanilla recipe book, which has no tab for this type and would otherwise log
     * an "unknown recipe category" warning per recipe; {@code ClientRecipeBook} skips special recipes
     * before it categorises anything. (Create's mechanical crafting does the same.) JEI is the discovery
     * and one-click placement path for these instead.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.SHAPED_5X5_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.SHAPED_5X5_TYPE.get();
    }
}
