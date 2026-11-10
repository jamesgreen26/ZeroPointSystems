package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * Reads {@link CondensingRecipe}s:
 *
 * <pre>{@code
 * {
 *   "type": "zps:condensing",
 *   "ingredient": { "gas": "vs_clockwork:steam", "amount": 1.0 },
 *   "result": { "id": "minecraft:ice" },
 *   "maxTemperature": 250.0,
 *   "temperatureCost": 100.0
 * }
 * }</pre>
 */
public class CondensingRecipeSerializer implements RecipeSerializer<CondensingRecipe> {

    private static final Codec<Double> TEMPERATURE_CODEC = Codec.DOUBLE.validate(value -> value >= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Temperatures are in Kelvin and cannot be negative, got " + value));

    private static final MapCodec<CondensingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GasOutput.CODEC.fieldOf("ingredient").forGetter(CondensingRecipe::ingredient),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(CondensingRecipe::result),
            TEMPERATURE_CODEC.fieldOf("maxTemperature").forGetter(CondensingRecipe::maxTemperature),
            TEMPERATURE_CODEC.fieldOf("temperatureCost").forGetter(CondensingRecipe::temperatureCost)
    ).apply(instance, CondensingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, CondensingRecipe> STREAM_CODEC = StreamCodec.composite(
            GasOutput.STREAM_CODEC, CondensingRecipe::ingredient,
            ItemStack.STREAM_CODEC, CondensingRecipe::result,
            ByteBufCodecs.DOUBLE, CondensingRecipe::maxTemperature,
            ByteBufCodecs.DOUBLE, CondensingRecipe::temperatureCost,
            CondensingRecipe::new);

    @Override
    public @NotNull MapCodec<CondensingRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, CondensingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
