package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VaporizingRecipeSerializer implements RecipeSerializer<VaporizingRecipe> {
    /** As many ingredients as the Vaporizer has slots. */
    public static final int MAX_INGREDIENTS = 3;

    private static final Codec<List<Ingredient>> INGREDIENTS_CODEC = Ingredient.CODEC_NONEMPTY.listOf()
            .validate(list -> {
                if (list.isEmpty()) {
                    return DataResult.error(() -> "A vaporizing recipe needs at least one ingredient");
                }
                if (list.size() > MAX_INGREDIENTS) {
                    return DataResult.error(() -> "A vaporizing recipe takes at most " + MAX_INGREDIENTS
                            + " ingredients, got " + list.size());
                }
                return DataResult.success(list);
            });

    private static final Codec<List<GasOutput>> RESULTS_CODEC = GasOutput.CODEC.listOf()
            .validate(list -> list.isEmpty()
                    ? DataResult.error(() -> "A vaporizing recipe needs at least one gas result")
                    : DataResult.success(list));

    private static final Codec<Double> TEMPERATURE_CODEC = Codec.DOUBLE.validate(value -> value >= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Temperatures are in Kelvin and cannot be negative, got " + value));

    private static final MapCodec<VaporizingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(VaporizingRecipe::ingredients),
            RESULTS_CODEC.fieldOf("results").forGetter(VaporizingRecipe::results),
            TEMPERATURE_CODEC.fieldOf("minTemperature").forGetter(VaporizingRecipe::minTemperature),
            TEMPERATURE_CODEC.fieldOf("temperatureCost").forGetter(VaporizingRecipe::temperatureCost)
    ).apply(instance, VaporizingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, VaporizingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), VaporizingRecipe::ingredients,
            GasOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), VaporizingRecipe::results,
            ByteBufCodecs.DOUBLE, VaporizingRecipe::minTemperature,
            ByteBufCodecs.DOUBLE, VaporizingRecipe::temperatureCost,
            VaporizingRecipe::new);

    @Override
    public @NotNull MapCodec<VaporizingRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, VaporizingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
