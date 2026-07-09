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
import net.minecraft.world.item.crafting.RecipeHolder;
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
        List<RecipeHolder<RollingRecipe>> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        registration.addRecipes(RollingMillCategory.TYPE, recipes);
    }

    /** Create JEI categories. Built from vanilla/JEI types only, so they link without Create present;
     * each equals-matches Create's registered category by uid + recipe class (RecipeHolder). */
    private static final RecipeType<?> CREATE_MECHANICAL_CRAFTING = createHolderType("mechanical_crafting");
    private static final RecipeType<?> CREATE_AUTOMATIC_SHAPED = createHolderType("automatic_shaped");

    private static RecipeType<?> createHolderType(String path) {
        return RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.ROLLING_MILL.get(), RollingMillCategory.TYPE);
        // The assembler is a workstation for vanilla crafting, plus Create's mechanical and automated
        // shaped crafting categories when Create is present.
        registration.addRecipeCatalyst(ModItems.ASSEMBLER.get(), RecipeTypes.CRAFTING);
        if (Compat.isCreateLoaded()) {
            registration.addRecipeCatalyst(ModItems.ASSEMBLER.get(), CREATE_MECHANICAL_CRAFTING);
            registration.addRecipeCatalyst(ModItems.ASSEMBLER.get(), CREATE_AUTOMATIC_SHAPED);
        }
    }
}
