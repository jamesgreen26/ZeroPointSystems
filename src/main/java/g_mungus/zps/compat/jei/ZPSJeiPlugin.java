package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.RollingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
        List<RollingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        registration.addRecipes(RollingMillCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ROLLING_MILL.get()), RollingMillCategory.TYPE);
        // The assembler is a workstation for vanilla crafting. (Create's own JEI plugin lists its
        // mechanical/automated crafting categories; JEI 15.x has no uid-only catalyst matching to add the
        // assembler to them without compile-coupling to Create, so only the vanilla catalyst is registered.)
        registration.addRecipeCatalyst(new ItemStack(ModItems.ASSEMBLER.get()), RecipeTypes.CRAFTING);
    }
}
