package g_mungus.zps.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

public class ThrusterFuelRecipeSerializer implements RecipeSerializer<ThrusterFuelRecipe> {

    @Override
    public @NotNull ThrusterFuelRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        ResourceLocation fuel = new ResourceLocation(GsonHelper.getAsString(json, "fuel"));
        double consumption = positive(json, "consumption");
        double thrust = positive(json, "thrust");
        ResourceLocation result = new ResourceLocation(GsonHelper.getAsString(json, "result"));
        double temperature = GsonHelper.getAsDouble(json, "temperature");
        if (temperature < 0) {
            throw new JsonSyntaxException("Temperatures are in Kelvin and cannot be negative, got " + temperature);
        }
        String flameName = GsonHelper.getAsString(json, "flame", ThrusterFlame.ORANGE.getSerializedName());
        ThrusterFlame flame = ThrusterFlame.CODEC.byName(flameName);
        if (flame == null) {
            throw new JsonSyntaxException("Unknown thruster flame " + flameName);
        }
        return new ThrusterFuelRecipe(recipeId, fuel, consumption, thrust, result, temperature, flame);
    }

    private static double positive(JsonObject json, String key) {
        double value = GsonHelper.getAsDouble(json, key);
        if (value <= 0) {
            throw new JsonSyntaxException("Consumption and thrust must be positive, got " + value);
        }
        return value;
    }

    @Override
    public ThrusterFuelRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        return new ThrusterFuelRecipe(recipeId, buffer.readResourceLocation(), buffer.readDouble(), buffer.readDouble(),
                buffer.readResourceLocation(), buffer.readDouble(), buffer.readEnum(ThrusterFlame.class));
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ThrusterFuelRecipe recipe) {
        buffer.writeResourceLocation(recipe.fuel());
        buffer.writeDouble(recipe.consumption());
        buffer.writeDouble(recipe.thrust());
        buffer.writeResourceLocation(recipe.result());
        buffer.writeDouble(recipe.temperature());
        buffer.writeEnum(recipe.flame());
    }
}
