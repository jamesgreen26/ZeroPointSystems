package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Serializer for {@link Shaped5x5Recipe}. The key/pattern format matches {@code minecraft:crafting_shaped}
 * exactly; the only difference is that patterns may be up to {@value #MAX_WIDTH}x{@value #MAX_HEIGHT}
 * (vanilla's pattern codec caps them at 3x3, which is the sole reason this codec exists).
 */
public class Shaped5x5RecipeSerializer implements RecipeSerializer<Shaped5x5Recipe> {
    public static final int MAX_WIDTH = AssemblerBlockEntity.GRID_WIDTH;
    public static final int MAX_HEIGHT = AssemblerBlockEntity.GRID_HEIGHT;

    private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(symbol -> {
        if (symbol.length() != 1) {
            return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
        }
        return " ".equals(symbol)
                ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
                : DataResult.success(symbol.charAt(0));
    }, String::valueOf);

    private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(rows -> {
        if (rows.isEmpty()) {
            return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
        }
        if (rows.size() > MAX_HEIGHT) {
            return DataResult.error(() -> "Invalid pattern: too many rows, %s is maximum".formatted(MAX_HEIGHT));
        }
        int width = rows.getFirst().length();
        for (String row : rows) {
            if (row.length() > MAX_WIDTH) {
                return DataResult.error(() -> "Invalid pattern: too many columns, %s is maximum".formatted(MAX_WIDTH));
            }
            if (row.length() != width) {
                return DataResult.error(() -> "Invalid pattern: each row must be the same width");
            }
        }
        return DataResult.success(rows);
    }, Function.identity());

    private static final MapCodec<ShapedRecipePattern.Data> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, Ingredient.CODEC_NONEMPTY).fieldOf("key")
                    .forGetter(ShapedRecipePattern.Data::key),
            PATTERN_CODEC.fieldOf("pattern").forGetter(ShapedRecipePattern.Data::pattern)
    ).apply(instance, ShapedRecipePattern.Data::new));

    /**
     * Decoding also verifies the pattern unpacks (every symbol defined and used). Encoding needs the raw
     * key/pattern the recipe was parsed from, so — as in vanilla — a recipe decoded from the network
     * cannot be re-encoded to JSON.
     */
    private static final MapCodec<Optional<ShapedRecipePattern.Data>> RECIPE_DATA_CODEC = DATA_CODEC.flatXmap(
            data -> unpack(data).map(pattern -> Optional.of(data)),
            data -> data.map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Cannot encode an unpacked 5x5 recipe")));

    private static final MapCodec<Shaped5x5Recipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RECIPE_DATA_CODEC.forGetter(Shaped5x5Recipe::data),
            Codec.STRING.optionalFieldOf("group", "").forGetter(Shaped5x5Recipe::getGroup),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Shaped5x5Recipe::result)
    ).apply(instance, (data, group, result) ->
            // Decoding always yields the raw data (see RECIPE_DATA_CODEC); only encoding can be dataless.
            new Shaped5x5Recipe(data.orElseThrow(), group, result)));

    private static final StreamCodec<RegistryFriendlyByteBuf, Shaped5x5Recipe> STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, Shaped5x5Recipe::pattern,
            ByteBufCodecs.STRING_UTF8, Shaped5x5Recipe::getGroup,
            ItemStack.STREAM_CODEC, Shaped5x5Recipe::result,
            Shaped5x5Recipe::new);

    /** Unpacks the key/pattern into a matcher, turning vanilla's thrown parse errors into a {@link DataResult}. */
    private static DataResult<ShapedRecipePattern> unpack(ShapedRecipePattern.Data data) {
        try {
            // Vanilla's own unpacking: it shrinks surrounding whitespace and enforces no size limit of its own.
            return DataResult.success(ShapedRecipePattern.of(data.key(), data.pattern()));
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            return DataResult.error(() -> message);
        }
    }

    @Override
    public @NotNull MapCodec<Shaped5x5Recipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Shaped5x5Recipe> streamCodec() {
        return STREAM_CODEC;
    }
}
