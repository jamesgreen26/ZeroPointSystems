package g_mungus.zps.recipe;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Serializer for {@link Shaped5x5Recipe}. The key/pattern format matches {@code minecraft:crafting_shaped}
 * exactly; the only difference is that patterns may be up to {@value #MAX_WIDTH}x{@value #MAX_HEIGHT}.
 *
 * <p>{@link ShapedRecipe}'s own pattern helpers are package-private and capped at 3x3 through its global
 * {@code MAX_WIDTH}/{@code MAX_HEIGHT}, so the parsing below is adapted from them rather than reused —
 * raising those fields with {@code ShapedRecipe.setCraftingSize} would loosen every vanilla shaped recipe.
 */
public class Shaped5x5RecipeSerializer implements RecipeSerializer<Shaped5x5Recipe> {
    public static final int MAX_WIDTH = AssemblerBlockEntity.GRID_WIDTH;
    public static final int MAX_HEIGHT = AssemblerBlockEntity.GRID_HEIGHT;

    @Override
    public @NotNull Shaped5x5Recipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        Map<String, Ingredient> key = keyFromJson(GsonHelper.getAsJsonObject(json, "key"));
        String[] pattern = shrink(patternFromJson(GsonHelper.getAsJsonArray(json, "pattern")));
        int width = pattern[0].length();
        int height = pattern.length;
        NonNullList<Ingredient> ingredients = dissolvePattern(pattern, key, width, height);
        ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
        return new Shaped5x5Recipe(recipeId, group, width, height, ingredients, result);
    }

    @Override
    public Shaped5x5Recipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        int width = buffer.readVarInt();
        int height = buffer.readVarInt();
        String group = buffer.readUtf();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int i = 0; i < ingredients.size(); i++) {
            ingredients.set(i, Ingredient.fromNetwork(buffer));
        }
        ItemStack result = buffer.readItem();
        return new Shaped5x5Recipe(recipeId, group, width, height, ingredients, result);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull Shaped5x5Recipe recipe) {
        buffer.writeVarInt(recipe.getWidth());
        buffer.writeVarInt(recipe.getHeight());
        buffer.writeUtf(recipe.getGroup());
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredient.toNetwork(buffer);
        }
        buffer.writeItem(recipe.result());
    }

    /** Symbol -> ingredient, with {@code ' '} reserved for an empty cell. */
    private static Map<String, Ingredient> keyFromJson(JsonObject json) {
        Map<String, Ingredient> key = Maps.newHashMap();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getKey().length() != 1) {
                throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey()
                        + "' is an invalid symbol (must be 1 character only).");
            }
            if (" ".equals(entry.getKey())) {
                throw new JsonSyntaxException("Invalid key entry: ' ' is a reserved symbol.");
            }
            key.put(entry.getKey(), Ingredient.fromJson(entry.getValue(), false));
        }
        key.put(" ", Ingredient.EMPTY);
        return key;
    }

    /** The raw pattern rows, bounded by the 5x5 grid and required to be rectangular. */
    private static String[] patternFromJson(JsonArray json) {
        String[] rows = new String[json.size()];
        if (rows.length > MAX_HEIGHT) {
            throw new JsonSyntaxException("Invalid pattern: too many rows, " + MAX_HEIGHT + " is maximum");
        }
        if (rows.length == 0) {
            throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
        }
        for (int i = 0; i < rows.length; i++) {
            String row = GsonHelper.convertToString(json.get(i), "pattern[" + i + "]");
            if (row.length() > MAX_WIDTH) {
                throw new JsonSyntaxException("Invalid pattern: too many columns, " + MAX_WIDTH + " is maximum");
            }
            if (i > 0 && rows[0].length() != row.length()) {
                throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
            }
            rows[i] = row;
        }
        return rows;
    }

    /** Trims the blank rows and columns surrounding the pattern. */
    private static String[] shrink(String... rows) {
        int firstColumn = Integer.MAX_VALUE;
        int lastColumn = 0;
        int blankLeadingRows = 0;
        int blankTrailingRows = 0;

        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            firstColumn = Math.min(firstColumn, firstNonSpace(row));
            int last = lastNonSpace(row);
            lastColumn = Math.max(lastColumn, last);
            if (last < 0) {
                if (blankLeadingRows == i) {
                    blankLeadingRows++;
                }
                blankTrailingRows++;
            } else {
                blankTrailingRows = 0;
            }
        }

        if (rows.length == blankTrailingRows) {
            return new String[0];
        }
        String[] shrunk = new String[rows.length - blankTrailingRows - blankLeadingRows];
        for (int i = 0; i < shrunk.length; i++) {
            shrunk[i] = rows[i + blankLeadingRows].substring(firstColumn, lastColumn + 1);
        }
        return shrunk;
    }

    /** Expands the pattern rows into a row-major ingredient list, rejecting undefined or unused symbols. */
    private static NonNullList<Ingredient> dissolvePattern(String[] pattern, Map<String, Ingredient> key,
                                                           int width, int height) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        Set<String> unused = Sets.newHashSet(key.keySet());
        unused.remove(" ");

        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                String symbol = pattern[row].substring(col, col + 1);
                Ingredient ingredient = key.get(symbol);
                if (ingredient == null) {
                    throw new JsonSyntaxException("Pattern references symbol '" + symbol
                            + "' but it's not defined in the key");
                }
                unused.remove(symbol);
                ingredients.set(col + width * row, ingredient);
            }
        }

        if (!unused.isEmpty()) {
            throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + unused);
        }
        return ingredients;
    }

    private static int firstNonSpace(String row) {
        int i = 0;
        while (i < row.length() && row.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int lastNonSpace(String row) {
        int i = row.length() - 1;
        while (i >= 0 && row.charAt(i) == ' ') {
            i--;
        }
        return i;
    }
}
