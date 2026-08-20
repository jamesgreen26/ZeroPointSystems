package g_mungus.zps.recipe;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ZPSMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ZPSMod.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<RollingRecipe>> ROLLING_TYPE =
            RECIPE_TYPES.register("rolling", () -> new RecipeType<RollingRecipe>() {
                @Override
                public String toString() {
                    return "zps:rolling";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RollingRecipeSerializer> ROLLING_SERIALIZER =
            RECIPE_SERIALIZERS.register("rolling", RollingRecipeSerializer::new);

    /** Shaped crafting on the Assembler's 5x5 grid. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<Shaped5x5Recipe>> SHAPED_5X5_TYPE =
            RECIPE_TYPES.register("shaped_5x5", () -> new RecipeType<Shaped5x5Recipe>() {
                @Override
                public String toString() {
                    return "zps:shaped_5x5";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, Shaped5x5RecipeSerializer> SHAPED_5X5_SERIALIZER =
            RECIPE_SERIALIZERS.register("shaped_5x5", Shaped5x5RecipeSerializer::new);

    /** Block-to-block transforms driven by the Impact Piston, with chance-weighted outcomes. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<ImpactRecipe>> IMPACT_TYPE =
            RECIPE_TYPES.register("impact", () -> new RecipeType<ImpactRecipe>() {
                @Override
                public String toString() {
                    return "zps:impact";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, ImpactRecipeSerializer> IMPACT_SERIALIZER =
            RECIPE_SERIALIZERS.register("impact", ImpactRecipeSerializer::new);
}
