package g_mungus.zps.recipe;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ZPSMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ZPSMod.MOD_ID);

    public static final RegistryObject<RecipeType<RollingRecipe>> ROLLING_TYPE =
            RECIPE_TYPES.register("rolling", () -> new RecipeType<RollingRecipe>() {
                @Override
                public String toString() {
                    return "zps:rolling";
                }
            });

    public static final RegistryObject<RollingRecipeSerializer> ROLLING_SERIALIZER =
            RECIPE_SERIALIZERS.register("rolling", RollingRecipeSerializer::new);

    /** Shaped crafting on the Assembler's 5x5 grid. */
    public static final RegistryObject<RecipeType<Shaped5x5Recipe>> SHAPED_5X5_TYPE =
            RECIPE_TYPES.register("shaped_5x5", () -> new RecipeType<Shaped5x5Recipe>() {
                @Override
                public String toString() {
                    return "zps:shaped_5x5";
                }
            });

    public static final RegistryObject<Shaped5x5RecipeSerializer> SHAPED_5X5_SERIALIZER =
            RECIPE_SERIALIZERS.register("shaped_5x5", Shaped5x5RecipeSerializer::new);
}
