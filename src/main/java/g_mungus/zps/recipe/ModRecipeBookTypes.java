package g_mungus.zps.recipe;

import net.minecraft.world.inventory.RecipeBookType;

/**
 * Custom recipe book tab types. On Forge 1.20.1 these are added to the vanilla {@link RecipeBookType}
 * enum via the {@code IExtensibleEnum} {@code create} hook (the equivalent of NeoForge's
 * enumextensions.json on the 1.21 branch).
 */
public final class ModRecipeBookTypes {
    public static final RecipeBookType ROLLING_MILL = RecipeBookType.create("ZPS_ROLLING_MILL");

    private ModRecipeBookTypes() {
    }
}
