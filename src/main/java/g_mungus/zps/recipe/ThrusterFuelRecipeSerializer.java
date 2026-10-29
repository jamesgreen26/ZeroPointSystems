package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

public class ThrusterFuelRecipeSerializer implements RecipeSerializer<ThrusterFuelRecipe> {

    private static final Codec<Double> POSITIVE_CODEC = Codec.DOUBLE.validate(value -> value > 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Consumption and thrust must be positive, got " + value));

    private static final Codec<Double> TEMPERATURE_CODEC = Codec.DOUBLE.validate(value -> value >= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Temperatures are in Kelvin and cannot be negative, got " + value));

    private static final MapCodec<ThrusterFuelRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fuel").forGetter(ThrusterFuelRecipe::fuel),
            POSITIVE_CODEC.fieldOf("consumption").forGetter(ThrusterFuelRecipe::consumption),
            POSITIVE_CODEC.fieldOf("thrust").forGetter(ThrusterFuelRecipe::thrust),
            ResourceLocation.CODEC.fieldOf("result").forGetter(ThrusterFuelRecipe::result),
            TEMPERATURE_CODEC.fieldOf("temperature").forGetter(ThrusterFuelRecipe::temperature),
            ThrusterFlame.CODEC.optionalFieldOf("flame", ThrusterFlame.ORANGE).forGetter(ThrusterFuelRecipe::flame)
    ).apply(instance, ThrusterFuelRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ThrusterFlame> FLAME_STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> ThrusterFlame.values()[i], ThrusterFlame::ordinal).cast();

    private static final StreamCodec<RegistryFriendlyByteBuf, ThrusterFuelRecipe> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ThrusterFuelRecipe::fuel,
            ByteBufCodecs.DOUBLE, ThrusterFuelRecipe::consumption,
            ByteBufCodecs.DOUBLE, ThrusterFuelRecipe::thrust,
            ResourceLocation.STREAM_CODEC, ThrusterFuelRecipe::result,
            ByteBufCodecs.DOUBLE, ThrusterFuelRecipe::temperature,
            FLAME_STREAM_CODEC, ThrusterFuelRecipe::flame,
            ThrusterFuelRecipe::new);

    @Override
    public @NotNull MapCodec<ThrusterFuelRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, ThrusterFuelRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
