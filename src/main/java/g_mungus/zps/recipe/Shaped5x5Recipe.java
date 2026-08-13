package g_mungus.zps.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Shaped crafting on a 5x5 grid, craftable only in the Assembler. Semantics are identical to
 * {@code minecraft:crafting_shaped} — keyed pattern rows, surrounding whitespace shrunk away, mirrored
 * matching, tag ingredients — but the pattern may be up to 5 wide and 5 tall.
 *
 * <p>The matching itself is delegated to vanilla's {@link ShapedRecipePattern}, which imposes no size
 * limit of its own; only its JSON codec caps patterns at 3x3, so {@link Shaped5x5RecipeSerializer}
 * supplies a 5x5 one instead.
 *
 * <p>Deliberately <em>not</em> a {@link net.minecraft.world.item.crafting.CraftingRecipe}: mods scan for
 * that interface to decide what their own machines can automate, ignoring the recipe type. Create's
 * mixer, for one, treats any non-{@code ShapedRecipe} {@code CraftingRecipe} as shapeless and would both
 * list these in "Automated Shapeless Crafting" and craft them in a basin.
 */
public class Shaped5x5Recipe implements Recipe<CraftingInput> {
    /**
     * The raw key/pattern this recipe was parsed from, kept solely so the codec can re-encode it (the
     * pattern itself can't be unpacked back into rows). Absent for recipes decoded from the network,
     * exactly like vanilla's shaped recipes.
     */
    private final Optional<ShapedRecipePattern.Data> data;
    private final ShapedRecipePattern pattern;
    private final String group;
    private final ItemStack result;

    /** Parsed from JSON: the pattern is unpacked from the raw key/pattern data. */
    public Shaped5x5Recipe(ShapedRecipePattern.Data data, String group, ItemStack result) {
        this(Optional.of(data), ShapedRecipePattern.of(data.key(), data.pattern()), group, result);
    }

    /** Decoded from the network: the unpacked pattern arrives directly. */
    public Shaped5x5Recipe(ShapedRecipePattern pattern, String group, ItemStack result) {
        this(Optional.empty(), pattern, group, result);
    }

    private Shaped5x5Recipe(Optional<ShapedRecipePattern.Data> data, ShapedRecipePattern pattern, String group,
                            ItemStack result) {
        this.data = data;
        this.pattern = pattern;
        this.group = group;
        this.result = result;
    }

    public Optional<ShapedRecipePattern.Data> data() {
        return data;
    }

    public ShapedRecipePattern pattern() {
        return pattern;
    }

    public ItemStack result() {
        return result;
    }

    public int getWidth() {
        return pattern.width();
    }

    public int getHeight() {
        return pattern.height();
    }

    @Override
    public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
        return pattern.matches(input);
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput input, HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
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
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.SHAPED_5X5_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.SHAPED_5X5_TYPE.get();
    }
}
