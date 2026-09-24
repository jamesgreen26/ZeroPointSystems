package g_mungus.zps.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VaporizingRecipeSerializer implements RecipeSerializer<VaporizingRecipe> {
    /** As many ingredients as the Vaporizer has slots. */
    public static final int MAX_INGREDIENTS = 3;

    @Override
    public @NotNull VaporizingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "ingredients")) {
            Ingredient ingredient = Ingredient.fromJson(element, false);
            if (ingredient.isEmpty()) {
                throw new JsonSyntaxException("A vaporizing recipe ingredient cannot be empty");
            }
            ingredients.add(ingredient);
        }
        if (ingredients.isEmpty()) {
            throw new JsonSyntaxException("A vaporizing recipe needs at least one ingredient");
        }
        if (ingredients.size() > MAX_INGREDIENTS) {
            throw new JsonSyntaxException("A vaporizing recipe takes at most " + MAX_INGREDIENTS
                    + " ingredients, got " + ingredients.size());
        }

        List<GasOutput> results = GasOutput.CODEC.listOf()
                .parse(JsonOps.INSTANCE, GsonHelper.getAsJsonArray(json, "results"))
                .getOrThrow(false, message -> {
                    throw new JsonSyntaxException("Invalid vaporizing recipe results: " + message);
                });
        if (results.isEmpty()) {
            throw new JsonSyntaxException("A vaporizing recipe needs at least one gas result");
        }

        double minTemperature = temperature(json, "minTemperature");
        double temperatureCost = temperature(json, "temperatureCost");
        return new VaporizingRecipe(recipeId, ingredients, results, minTemperature, temperatureCost);
    }

    private static double temperature(JsonObject json, String key) {
        double value = GsonHelper.getAsDouble(json, key);
        if (value < 0) {
            throw new JsonSyntaxException("Temperatures are in Kelvin and cannot be negative, got " + value);
        }
        return value;
    }

    @Override
    public VaporizingRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        List<Ingredient> ingredients = buffer.readList(Ingredient::fromNetwork);
        List<GasOutput> results = buffer.readList(GasOutput::read);
        double minTemperature = buffer.readDouble();
        double temperatureCost = buffer.readDouble();
        return new VaporizingRecipe(recipeId, ingredients, results, minTemperature, temperatureCost);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull VaporizingRecipe recipe) {
        buffer.writeCollection(recipe.ingredients(), (buf, ingredient) -> ingredient.toNetwork(buf));
        buffer.writeCollection(recipe.results(), GasOutput::write);
        buffer.writeDouble(recipe.minTemperature());
        buffer.writeDouble(recipe.temperatureCost());
    }
}
