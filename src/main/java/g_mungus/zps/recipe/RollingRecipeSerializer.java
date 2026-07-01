package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

public class RollingRecipeSerializer implements RecipeSerializer<RollingRecipe> {
    public static final int DEFAULT_PROCESS_TIME = 200;
    public static final int DEFAULT_ENERGY_PER_TICK = 32;

    private static final MapCodec<RollingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(RollingRecipe::ingredient),
            ItemStack.CODEC.fieldOf("result").forGetter(RollingRecipe::result),
            Codec.INT.optionalFieldOf("processTime", DEFAULT_PROCESS_TIME).forGetter(RollingRecipe::processTime),
            Codec.INT.optionalFieldOf("energyPerTick", DEFAULT_ENERGY_PER_TICK).forGetter(RollingRecipe::energyPerTick)
    ).apply(instance, RollingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, RollingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, RollingRecipe::ingredient,
            ItemStack.STREAM_CODEC, RollingRecipe::result,
            ByteBufCodecs.INT, RollingRecipe::processTime,
            ByteBufCodecs.INT, RollingRecipe::energyPerTick,
            RollingRecipe::new);

    @Override
    public @NotNull MapCodec<RollingRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, RollingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
