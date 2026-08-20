package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ImpactRecipeSerializer implements RecipeSerializer<ImpactRecipe> {
    /** At least one outcome is required, otherwise there is nothing for a strike to produce. */
    private static final Codec<List<ImpactResult>> RESULTS_CODEC = ImpactResult.CODEC.listOf()
            .validate(results -> results.isEmpty()
                    ? DataResult.error(() -> "An impact recipe needs at least one result")
                    : DataResult.success(results));

    private static final MapCodec<ImpactRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Accepts a block id, a "#block_tag", or a list of either.
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("ingredient").forGetter(ImpactRecipe::ingredient),
            RESULTS_CODEC.fieldOf("results").forGetter(ImpactRecipe::results)
    ).apply(instance, ImpactRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ImpactRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.BLOCK), ImpactRecipe::ingredient,
            ImpactResult.STREAM_CODEC.apply(ByteBufCodecs.list()), ImpactRecipe::results,
            ImpactRecipe::new);

    @Override
    public @NotNull MapCodec<ImpactRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, ImpactRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
