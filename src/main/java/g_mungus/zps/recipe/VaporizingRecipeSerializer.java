package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VaporizingRecipeSerializer implements RecipeSerializer<VaporizingRecipe> {

    public static final double DEFAULT_TEMPERATURE = 900.0;
    public static final int DEFAULT_PROCESS_TIME = 100;
    public static final int DEFAULT_ENERGY_PER_TICK = 32;

    private static final MapCodec<VaporizingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.listOf(1, 2).fieldOf("ingredients")
                    .forGetter(VaporizingRecipe::ingredientList),
            ResourceLocation.CODEC.fieldOf("gas").forGetter(VaporizingRecipe::gas),
            Codec.DOUBLE.fieldOf("amount").forGetter(VaporizingRecipe::amount),
            Codec.DOUBLE.optionalFieldOf("temperature", DEFAULT_TEMPERATURE)
                    .forGetter(VaporizingRecipe::temperature),
            Codec.INT.optionalFieldOf("processTime", DEFAULT_PROCESS_TIME)
                    .forGetter(VaporizingRecipe::processTime),
            Codec.INT.optionalFieldOf("energyPerTick", DEFAULT_ENERGY_PER_TICK)
                    .forGetter(VaporizingRecipe::energyPerTick)
    ).apply(instance, VaporizingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, VaporizingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    VaporizingRecipe::ingredientList,
                    ResourceLocation.STREAM_CODEC, VaporizingRecipe::gas,
                    ByteBufCodecs.DOUBLE, VaporizingRecipe::amount,
                    ByteBufCodecs.DOUBLE, VaporizingRecipe::temperature,
                    ByteBufCodecs.INT, VaporizingRecipe::processTime,
                    ByteBufCodecs.INT, VaporizingRecipe::energyPerTick,
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
