package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.RollingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
        registration.addRecipeCategories(new RollingMillCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<RollingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        registration.addRecipes(RollingMillCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ROLLING_MILL.get()), RollingMillCategory.TYPE);

        // The assembler is a workstation for vanilla crafting, plus Create's mechanical and automated
        // shaped crafting categories when Create is present.
        ItemStack assembler = new ItemStack(ModItems.ASSEMBLER.get());
        registration.addRecipeCatalyst(assembler, RecipeTypes.CRAFTING);
        if (Compat.isCreateLoaded()) {
            registration.addRecipeCatalyst(assembler, CREATE_MECHANICAL_CRAFTING);
            registration.addRecipeCatalyst(assembler, CREATE_AUTOMATIC_SHAPED);
        }
    }
}
