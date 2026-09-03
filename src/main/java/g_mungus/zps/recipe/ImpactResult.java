package g_mungus.zps.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
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
 *                   int provider, so {@code {"type": "minecraft:uniform", "value": {"min_inclusive":
 *                   1, "max_inclusive": 2}}} buries one or two.
 */
public record ImpactResult(Holder<Block> block, int weight, Optional<Ingredient> buriedItem, IntProvider count) {
    public static final int DEFAULT_WEIGHT = 1;
    public static final IntProvider DEFAULT_COUNT = ConstantInt.of(1);
    /** Nothing may be buried in a quantity a single stack cannot hold. */
    public static final int MAX_COUNT = 64;

    public ImpactResult(Holder<Block> block, int weight, Optional<Ingredient> buriedItem) {
        this(block, weight, buriedItem, DEFAULT_COUNT);
    }

    public static ImpactResult of(Block block, int weight, Optional<Ingredient> buriedItem, IntProvider count) {
        return new ImpactResult(BuiltInRegistries.BLOCK.wrapAsHolder(block), weight, buriedItem, count);
    }
}
