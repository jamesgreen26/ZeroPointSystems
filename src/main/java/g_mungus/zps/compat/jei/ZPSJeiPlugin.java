package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ImpactRecipe;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.RollingRecipe;
import g_mungus.zps.recipe.Shaped5x5Recipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Optional JEI integration. JEI only instantiates {@code @JeiPlugin} classes when it is installed,
 * so this and {@link RollingMillCategory} are never loaded otherwise.
 */
@JeiPlugin
public class ZPSJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ZPSMod.resource("jei");

    // Create's mechanical-crafting and automatic-shaped JEI categories are both keyed on the vanilla
    // CraftingRecipe class (verified against Create's CreateJEI), so matching RecipeTypes can be rebuilt
    // from just the create: id + CraftingRecipe.class — no Create classes are referenced or loaded. These
    // are only registered when Create is present (below), since the categories don't exist otherwise.
    private static final RecipeType<CraftingRecipe> CREATE_MECHANICAL_CRAFTING =
            new RecipeType<>(ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafting"), CraftingRecipe.class);
    private static final RecipeType<CraftingRecipe> CREATE_AUTOMATIC_SHAPED =
            new RecipeType<>(ResourceLocation.fromNamespaceAndPath("create", "automatic_shaped"), CraftingRecipe.class);

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new RollingMillCategory(guiHelper),
                new Shaped5x5Category(guiHelper),
                new ImpactCategory(guiHelper));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RecipeManager recipeManager = level.getRecipeManager();
        List<RollingRecipe> recipes = recipeManager.getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        registration.addRecipes(RollingMillCategory.TYPE, recipes);
        List<Shaped5x5Recipe> shaped5x5 = recipeManager.getAllRecipesFor(ModRecipes.SHAPED_5X5_TYPE.get());
        registration.addRecipes(Shaped5x5Category.TYPE, shaped5x5);
        List<ImpactRecipe> impact = recipeManager.getAllRecipesFor(ModRecipes.IMPACT_TYPE.get());
        registration.addRecipes(ImpactCategory.TYPE, impact);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ROLLING_MILL.get()), RollingMillCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.IMPACT_PISTON.get()), ImpactCategory.TYPE);

        // The assembler is a workstation for its own 5x5 recipes and vanilla crafting, plus Create's
        // mechanical and automated shaped crafting categories when Create is present.
        ItemStack assembler = new ItemStack(ModItems.ASSEMBLER.get());
        registration.addRecipeCatalyst(assembler, Shaped5x5Category.TYPE);
        registration.addRecipeCatalyst(assembler, RecipeTypes.CRAFTING);
        if (Compat.isCreateLoaded()) {
            registration.addRecipeCatalyst(assembler, CREATE_MECHANICAL_CRAFTING);
            registration.addRecipeCatalyst(assembler, CREATE_AUTOMATIC_SHAPED);
        }
    }

    /** The "+" button on both categories the assembler can craft, stamping the recipe into its pattern. */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(
                new AssemblerTransferHandler<>(helper, Shaped5x5Category.TYPE), Shaped5x5Category.TYPE);
        registration.addRecipeTransferHandler(
                new AssemblerTransferHandler<>(helper, RecipeTypes.CRAFTING), RecipeTypes.CRAFTING);
    }
}
