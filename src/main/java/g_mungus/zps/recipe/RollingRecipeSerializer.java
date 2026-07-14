package g_mungus.zps.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

public class RollingRecipeSerializer implements RecipeSerializer<RollingRecipe> {
    public static final int DEFAULT_PROCESS_TIME = 200;
    public static final int DEFAULT_ENERGY_PER_TICK = 32;

    @Override
    public @NotNull RollingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"), false);
        ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
        int processTime = GsonHelper.getAsInt(json, "processTime", DEFAULT_PROCESS_TIME);
        int energyPerTick = GsonHelper.getAsInt(json, "energyPerTick", DEFAULT_ENERGY_PER_TICK);
        return new RollingRecipe(recipeId, ingredient, result, processTime, energyPerTick);
    }

    @Override
    public RollingRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        Ingredient ingredient = Ingredient.fromNetwork(buffer);
        ItemStack result = buffer.readItem();
        int processTime = buffer.readVarInt();
        int energyPerTick = buffer.readVarInt();
        return new RollingRecipe(recipeId, ingredient, result, processTime, energyPerTick);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull RollingRecipe recipe) {
        recipe.ingredient().toNetwork(buffer);
        buffer.writeItem(recipe.result());
        buffer.writeVarInt(recipe.processTime());
        buffer.writeVarInt(recipe.energyPerTick());
    }
}
