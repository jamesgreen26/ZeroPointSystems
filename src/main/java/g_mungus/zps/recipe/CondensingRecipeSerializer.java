package g_mungus.zps.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * Reads {@link CondensingRecipe}s:
 *
 * <pre>{@code
 * {
 *   "type": "zps:condensing",
 *   "ingredient": { "gas": "vs_clockwork:steam", "amount": 1.0 },
 *   "result": { "item": "minecraft:ice" },
 *   "maxTemperature": 250.0,
 *   "temperatureCost": 100.0
 * }
 * }</pre>
 */
public class CondensingRecipeSerializer implements RecipeSerializer<CondensingRecipe> {

    @Override
    public @NotNull CondensingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        GasOutput ingredient = GasOutput.CODEC
                .parse(JsonOps.INSTANCE, GsonHelper.getAsJsonObject(json, "ingredient"))
                .getOrThrow(false, message -> {
                    throw new JsonSyntaxException("Invalid condensing recipe ingredient: " + message);
                });

        ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
        if (result.isEmpty()) {
            throw new JsonSyntaxException("A condensing recipe needs an item result");
        }

        double maxTemperature = temperature(json, "maxTemperature");
        double temperatureCost = temperature(json, "temperatureCost");
        return new CondensingRecipe(recipeId, ingredient, result, maxTemperature, temperatureCost);
    }

    private static double temperature(JsonObject json, String key) {
        double value = GsonHelper.getAsDouble(json, key);
        if (value < 0) {
            throw new JsonSyntaxException("Temperatures are in Kelvin and cannot be negative, got " + value);
        }
        return value;
    }

    @Override
    public CondensingRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        GasOutput ingredient = GasOutput.read(buffer);
        ItemStack result = buffer.readItem();
        double maxTemperature = buffer.readDouble();
        double temperatureCost = buffer.readDouble();
        return new CondensingRecipe(recipeId, ingredient, result, maxTemperature, temperatureCost);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull CondensingRecipe recipe) {
        GasOutput.write(buffer, recipe.ingredient());
        buffer.writeItem(recipe.result());
        buffer.writeDouble(recipe.maxTemperature());
        buffer.writeDouble(recipe.temperatureCost());
    }
}
