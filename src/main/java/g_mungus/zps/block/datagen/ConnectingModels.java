package g_mungus.zps.block.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.Set;

import static g_mungus.zps.block.datagen.BlockDataGenerator.blocksToDatagen;

/**
 * Puts the plain and colored space plating models on the {@code zps:model} connecting loader. Every shape of one colour
 * (block, slab, stairs, wall) joins up with the others, wherever they carry on the same flat surface.
 */
public class ConnectingModels {
    public static final Set<String> CONNECTING = Set.of(
            "space_plating", "red_space_plating", "orange_space_plating", "yellow_space_plating",
            "green_space_plating", "lime_space_plating", "cyan_space_plating", "blue_space_plating",
            "light_blue_space_plating", "purple_space_plating", "magenta_space_plating", "pink_space_plating",
            "black_space_plating", "brown_space_plating", "gray_space_plating", "light_gray_space_plating",
            "chrome_space_plating", "agate_space_plating");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Returns the model JSON for a block of the {@code name} family, on the connecting loader if it connects. */
    static String wrap(String name, String json) {
        if (!CONNECTING.contains(name)) {
            return json;
        }
        JsonObject model = new JsonObject();
        model.addProperty("loader", "zps:model");
        model.addProperty("type", "connecting");
        model.addProperty("match_surface", true);
        JsonArray connections = new JsonArray();
        for (BlockType type : blocksToDatagen.get(name)) {
            JsonObject connection = new JsonObject();
            connection.addProperty("type", "match_block");
            connection.addProperty("block", "zps:" + BlockDataGenerator.blockName(name, type));
            connections.add(connection);
        }
        model.add("connections", connections);
        for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            model.add(entry.getKey(), entry.getValue());
        }
        return GSON.toJson(model) + "\n";
    }
}
