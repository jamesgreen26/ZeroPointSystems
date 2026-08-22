package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * One possible outcome of an {@link ImpactRecipe}, picked against the other outcomes in proportion
 * to its {@link #weight}.
 *
 * @param block      the block the struck block is replaced with
 * @param weight     relative likelihood of this outcome; defaults to 1
 * @param buriedItem when present and {@code block} has a {@code BrushableBlockEntity}, one item is
 *                   drawn at random from this ingredient and buried inside it. Lets a recipe produce
 *                   e.g. suspicious gravel holding a random nugget.
 * @param count      how many of {@code buriedItem} to bury, rolled per strike; defaults to 1 and is
 *                   ignored when there is no buried item. Written either as a plain number or as any
 *                   int provider, so {@code {"type": "minecraft:uniform", "min_inclusive": 1,
 *                   "max_inclusive": 2}} buries one or two.
 */
public record ImpactResult(Holder<Block> block, int weight, Optional<Ingredient> buriedItem, IntProvider count) {
    public static final int DEFAULT_WEIGHT = 1;
    /** Shared instance so {@code optionalFieldOf} can recognise — and omit — an unset count. */
    public static final IntProvider DEFAULT_COUNT = ConstantInt.of(1);

    public ImpactResult(Holder<Block> block, int weight, Optional<Ingredient> buriedItem) {
        this(block, weight, buriedItem, DEFAULT_COUNT);
    }

    public static final Codec<ImpactResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("block").forGetter(ImpactResult::block),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", DEFAULT_WEIGHT).forGetter(ImpactResult::weight),
            Ingredient.CODEC.optionalFieldOf("buried_item").forGetter(ImpactResult::buriedItem),
            IntProvider.codec(1, Item.ABSOLUTE_MAX_STACK_SIZE).optionalFieldOf("count", DEFAULT_COUNT).forGetter(ImpactResult::count)
    ).apply(instance, ImpactResult::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImpactResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.BLOCK), ImpactResult::block,
            ByteBufCodecs.VAR_INT, ImpactResult::weight,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs::optional), ImpactResult::buriedItem,
            ByteBufCodecs.fromCodec(IntProvider.CODEC), ImpactResult::count,
            ImpactResult::new);
}
