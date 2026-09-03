package g_mungus.zps.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads {@code zps:impact} recipes.
 *
 * <p>{@code ingredient} is either a single block id, a single {@code "#block_tag"}, or an array of
 * block ids. A tag inside an array is rejected: a tag is a live view of whatever the datapack puts
 * in it, and there is nothing to merge it into a fixed list with at load time. Every shipped recipe
 * uses the single-entry forms.
 */
public class ImpactRecipeSerializer implements RecipeSerializer<ImpactRecipe> {
    private static final String COUNT_TAG = "count";

    @Override
    public @NotNull ImpactRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        HolderSet<Block> ingredient = readIngredient(json.get("ingredient"));
        List<ImpactResult> results = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "results")) {
            results.add(readResult(GsonHelper.convertToJsonObject(element, "result")));
        }
        if (results.isEmpty()) {
            throw new JsonSyntaxException("An impact recipe needs at least one result");
        }
        return new ImpactRecipe(recipeId, ingredient, results);
    }

    private static HolderSet<Block> readIngredient(JsonElement element) {
        if (element == null) {
            throw new JsonSyntaxException("Missing ingredient");
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Holder<Block>> blocks = new ArrayList<>(array.size());
            for (JsonElement entry : array) {
                String id = GsonHelper.convertToString(entry, "ingredient");
                if (id.startsWith("#")) {
                    throw new JsonSyntaxException("Block tag " + id + " must be the whole ingredient, not one entry of a list");
                }
                blocks.add(block(id).builtInRegistryHolder());
            }
            if (blocks.isEmpty()) {
                throw new JsonSyntaxException("An impact recipe needs at least one ingredient block");
            }
            return HolderSet.direct(blocks);
        }
        String id = GsonHelper.convertToString(element, "ingredient");
        if (id.startsWith("#")) {
            return BuiltInRegistries.BLOCK.getOrCreateTag(
                    TagKey.create(Registries.BLOCK, parseId(id.substring(1))));
        }
        return HolderSet.direct(block(id).builtInRegistryHolder());
    }

    private static Block block(String id) {
        ResourceLocation location = parseId(id);
        return BuiltInRegistries.BLOCK.getOptional(location)
                .orElseThrow(() -> new JsonSyntaxException("Unknown block " + location));
    }

    private static ResourceLocation parseId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new JsonSyntaxException("Not a valid resource location: " + id);
        }
        return location;
    }

    private static ImpactResult readResult(JsonObject json) {
        Block block = block(GsonHelper.getAsString(json, "block"));
        int weight = GsonHelper.getAsInt(json, "weight", ImpactResult.DEFAULT_WEIGHT);
        if (weight <= 0) {
            throw new JsonSyntaxException("An impact result weight must be positive, got " + weight);
        }
        Optional<Ingredient> buried = json.has("buried_item")
                ? Optional.of(Ingredient.fromJson(json.get("buried_item"), false))
                : Optional.empty();
        IntProvider count = json.has(COUNT_TAG)
                ? IntProvider.codec(1, ImpactResult.MAX_COUNT)
                        .parse(JsonOps.INSTANCE, json.get(COUNT_TAG))
                        .getOrThrow(false, message -> {
                            throw new JsonSyntaxException("Invalid impact result count: " + message);
                        })
                : ImpactResult.DEFAULT_COUNT;
        return ImpactResult.of(block, weight, buried, count);
    }

    @Override
    public ImpactRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        HolderSet<Block> ingredient;
        if (buffer.readBoolean()) {
            ingredient = BuiltInRegistries.BLOCK.getOrCreateTag(
                    TagKey.create(Registries.BLOCK, buffer.readResourceLocation()));
        } else {
            int size = buffer.readVarInt();
            List<Holder<Block>> blocks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                blocks.add(buffer.readById(BuiltInRegistries.BLOCK).builtInRegistryHolder());
            }
            ingredient = HolderSet.direct(blocks);
        }
        int resultCount = buffer.readVarInt();
        List<ImpactResult> results = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            Block block = buffer.readById(BuiltInRegistries.BLOCK);
            int weight = buffer.readVarInt();
            Optional<Ingredient> buried = buffer.readBoolean()
                    ? Optional.of(Ingredient.fromNetwork(buffer))
                    : Optional.empty();
            results.add(ImpactResult.of(block, weight, buried, readCount(buffer)));
        }
        return new ImpactRecipe(recipeId, ingredient, results);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ImpactRecipe recipe) {
        HolderSet<Block> ingredient = recipe.ingredient();
        if (ingredient instanceof HolderSet.Named<Block> named) {
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(named.key().location());
        } else {
            buffer.writeBoolean(false);
            buffer.writeVarInt(ingredient.size());
            for (Holder<Block> holder : ingredient) {
                buffer.writeId(BuiltInRegistries.BLOCK, holder.value());
            }
        }
        List<ImpactResult> results = recipe.results();
        buffer.writeVarInt(results.size());
        for (ImpactResult result : results) {
            buffer.writeId(BuiltInRegistries.BLOCK, result.block().value());
            buffer.writeVarInt(result.weight());
            buffer.writeBoolean(result.buriedItem().isPresent());
            result.buriedItem().ifPresent(buried -> buried.toNetwork(buffer));
            writeCount(buffer, result.count());
        }
    }

    /**
     * Int providers have no stream codec of their own in this version, so the count travels as the
     * NBT form of its regular codec.
     */
    private static void writeCount(FriendlyByteBuf buffer, IntProvider count) {
        Tag encoded = IntProvider.CODEC.encodeStart(NbtOps.INSTANCE, count)
                .getOrThrow(false, message -> {
                    throw new IllegalStateException("Could not encode impact result count: " + message);
                });
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(COUNT_TAG, encoded);
        buffer.writeNbt(wrapper);
    }

    private static IntProvider readCount(FriendlyByteBuf buffer) {
        CompoundTag wrapper = buffer.readNbt();
        if (wrapper == null || !wrapper.contains(COUNT_TAG)) {
            return ImpactResult.DEFAULT_COUNT;
        }
        return IntProvider.CODEC.parse(NbtOps.INSTANCE, wrapper.get(COUNT_TAG))
                .getOrThrow(false, message -> {
                    throw new IllegalStateException("Could not decode impact result count: " + message);
                });
    }
}
