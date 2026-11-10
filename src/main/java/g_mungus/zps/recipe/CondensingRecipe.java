package g_mungus.zps.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A "condensing" recipe: the Cryo Condenser freezes a quantity of one gas into an item.
 *
 * <p>Each recipe takes exactly one gas, so two recipes never compete over a mix of ingredients:
 * the machine can weigh each one on its own. The gas is matched against what sits at the
 * machine's node, not against a container, so {@link #matches} is never what decides a craft.
 *
 * <p>The machine must be at or below {@link #maxTemperature} to run the recipe, and each craft
 * then warms it by {@link #temperatureCost} scaled by how hot the gas was: see
 * {@link #costAt(double)}.
 */
public class CondensingRecipe implements Recipe<Container> {
    /**
     * The gas temperature, in Kelvin, at which a craft costs exactly {@link #temperatureCost}:
     * roughly what Steam leaves a Vaporizer at.
     */
    public static final double REFERENCE_GAS_TEMPERATURE_K = 373.0;

    private final ResourceLocation id;
    private final GasOutput ingredient;
    private final ItemStack result;
    private final double maxTemperature;
    private final double temperatureCost;

    public CondensingRecipe(ResourceLocation id, GasOutput ingredient, ItemStack result,
                            double maxTemperature, double temperatureCost) {
        this.id = id;
        this.ingredient = ingredient;
        this.result = result.copy();
        this.maxTemperature = maxTemperature;
        this.temperatureCost = temperatureCost;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    /** The gas this recipe freezes, and how much of it one craft takes, in kilograms. */
    public GasOutput ingredient() {
        return ingredient;
    }

    /** What one craft makes. A copy, so callers may keep or change it. */
    public ItemStack result() {
        return result.copy();
    }

    /** The machine temperature, in Kelvin, above which this recipe will not run. */
    public double maxTemperature() {
        return maxTemperature;
    }

    /** How far, in Kelvin, the machine warms for each craft of gas at {@link #REFERENCE_GAS_TEMPERATURE_K}. */
    public double temperatureCost() {
        return temperatureCost;
    }

    /**
     * How far, in Kelvin, one craft warms the machine when the gas is at {@code gasTemperatureK}:
     * the cost scaled by the gas's absolute temperature, so cooler gas is cheaper to freeze but
     * never free.
     */
    public double costAt(double gasTemperatureK) {
        return temperatureCost * Math.max(0, gasTemperatureK) / REFERENCE_GAS_TEMPERATURE_K;
    }

    /** Matched by the machine against the gas at its node, never against a container. */
    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess registries) {
        return result();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** No item ingredients: the input is gas. */
    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull RegistryAccess registries) {
        return result();
    }

    /** Made by a machine from gas, so keep it out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.CONDENSING_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.CONDENSING_TYPE.get();
    }
}
