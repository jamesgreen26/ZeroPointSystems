package g_mungus.zps.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import g_mungus.zps.ZPSMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills {@link #STONE_ORES_IN_GROUND_DROPS} at the end of every data load with the items named by
 * the loot table of each block in {@code #forge:ores_in_ground/stone}: raw iron for iron ore, diamond
 * for diamond ore, and whatever a modded ore's table says for that.
 *
 * <p>The tag is what the ore impact recipe points its {@code buried_item} at, so JEI can show what
 * the suspicious gravel may hold through the same path as every hand-written suspicious recipe,
 * and modded ores appear there without anyone listing them. It is an ordinary tag otherwise: a
 * datapack may add to it, and the additions made here are synced to clients with the rest.
 *
 * <p>Rolling a loot table needs a level, and none exists during the first data load, so the tables
 * are read rather than rolled: each is encoded back to its JSON form and searched for item entries.
 * That over-reports slightly, since conditions are ignored, which is fine for a "may contain" list.
 * The one entry that would mislead, the silk touch branch handing back the ore itself, is dropped.
 *
 * <p>Tags are data, with no hook for computed entries, so this waits for {@link TagsUpdatedEvent},
 * by which point both the tags and the loot tables of the load are final, and rebinds the item
 * tags with the extra entries folded in. Clients are only sent tags after that event.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class OreDropsTags {
    public static final TagKey<Item> STONE_ORES_IN_GROUND_DROPS =
            TagKey.create(Registries.ITEM, ZPSMod.resource("stone_ores_in_ground_drops"));

    private static final String ITEM_ENTRY = "minecraft:item";
    private static final String TAG_ENTRY = "minecraft:tag";
    private static final String TABLE_ENTRY = "minecraft:loot_table";

    /** Loot tables have no codec in this version; their Gson serializer writes the same JSON shape. */
    private static final Gson LOOT_GSON = Deserializers.createLootTableSerializer().create();

    /** The resources of the load in progress. The tags event does not carry them, so they are picked up here. */
    @Nullable
    private static ReloadableServerResources pendingResources;

    private OreDropsTags() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        pendingResources = event.getServerResources();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || pendingResources == null) {
            return;
        }
        Set<Item> drops = lootItems(pendingResources.getLootData(), Tags.Blocks.ORES_IN_GROUND_STONE);
        addToTag(BuiltInRegistries.ITEM, STONE_ORES_IN_GROUND_DROPS, drops);
    }

    /** Every item the loot tables of the blocks in {@code ores} can hand out, other than the ores themselves. */
    private static Set<Item> lootItems(LootDataManager lootData, TagKey<Block> ores) {
        Set<Item> results = new LinkedHashSet<>();
        for (Holder<Block> ore : BuiltInRegistries.BLOCK.getTagOrEmpty(ores)) {
            Set<Item> drops = new LinkedHashSet<>();
            collectTable(lootData, ore.value().getLootTable(), drops, new HashSet<>());
            drops.remove(ore.value().asItem());
            results.addAll(drops);
        }
        results.remove(Items.AIR);
        return results;
    }

    private static void collectTable(LootDataManager lootData, ResourceLocation key,
                                     Set<Item> into, Set<ResourceLocation> visited) {
        // Tables can reference one another, so a cycle has to stop somewhere.
        if (!visited.add(key)) {
            return;
        }
        LootTable table = lootData.getLootTable(key);
        if (table == LootTable.EMPTY) {
            return;
        }
        JsonElement json;
        try {
            json = LOOT_GSON.toJsonTree(table);
        } catch (RuntimeException error) {
            ZPSMod.LOGGER.warn("Could not read loot table {} for {}: {}",
                    key, STONE_ORES_IN_GROUND_DROPS.location(), error.getMessage());
            return;
        }
        collectEntries(lootData, json, into, visited);
    }

    /**
     * Walks the whole encoded table rather than just its pools' top-level entries, because entries
     * nest: an ore's table is an {@code alternatives} entry wrapping the silk touch and plain drops.
     */
    private static void collectEntries(LootDataManager lootData, JsonElement json,
                                       Set<Item> into, Set<ResourceLocation> visited) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collectEntries(lootData, element, into, visited));
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        String type = GsonHelper.getAsString(object, "type", "");
        if (type.equals(ITEM_ENTRY) && GsonHelper.isStringValue(object, "name")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(object, "name"));
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(into::add);
            }
        } else if (type.equals(TAG_ENTRY) && GsonHelper.isStringValue(object, "name")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(object, "name"));
            if (id != null) {
                BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id)).forEach(item -> into.add(item.value()));
            }
        } else if (type.equals(TABLE_ENTRY) && GsonHelper.isStringValue(object, "value")) {
            // A reference by id. An inline table is a plain object, which the walk below covers.
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(object, "value"));
            if (id != null) {
                collectTable(lootData, id, into, visited);
            }
        }
        object.entrySet().forEach(entry -> collectEntries(lootData, entry.getValue(), into, visited));
    }

    /**
     * {@code bindTags} resets every holder's tag list from the map it is given, so the additions
     * have to go in alongside a copy of everything already bound rather than on their own.
     */
    private static <T> void addToTag(Registry<T> registry, TagKey<T> tag, Set<T> additions) {
        if (additions.isEmpty()) {
            return;
        }
        Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
        registry.getTags().forEach(pair -> tags.put(pair.getFirst(), pair.getSecond().stream().toList()));

        Set<Holder<T>> merged = new LinkedHashSet<>(tags.getOrDefault(tag, List.of()));
        for (T addition : additions) {
            merged.add(registry.wrapAsHolder(addition));
        }
        tags.put(tag, new ArrayList<>(merged));
        registry.bindTags(tags);
    }
}
