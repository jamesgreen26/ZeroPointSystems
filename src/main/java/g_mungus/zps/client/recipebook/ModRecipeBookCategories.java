package g_mungus.zps.client.recipebook;

import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Custom recipe book categories for the Rolling Mill tab. On Forge 1.20.1 these are added to the vanilla
 * {@link RecipeBookCategories} enum via the {@code IExtensibleEnum} {@code create} hook, passing the tab
 * icons directly (the equivalent of the icon suppliers wired through enumextensions.json on the 1.21 branch).
 */
public final class ModRecipeBookCategories {
    public static final RecipeBookCategories ROLLING_MILL_SEARCH =
            RecipeBookCategories.create("ZPS_ROLLING_MILL_SEARCH", new ItemStack(Items.COMPASS));
    public static final RecipeBookCategories ROLLING_MILL =
            RecipeBookCategories.create("ZPS_ROLLING_MILL", new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_NUGGET));

    private ModRecipeBookCategories() {
    }
}
