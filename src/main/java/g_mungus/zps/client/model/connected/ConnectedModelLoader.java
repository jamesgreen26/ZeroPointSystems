package g_mungus.zps.client.model.connected;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConnectedModelLoader implements IGeometryLoader<ConnectedGeometry> {
    public static final ConnectedModelLoader INSTANCE = new ConnectedModelLoader();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zps", "model");

    private ConnectedModelLoader() {}

    @Override
    public ConnectedGeometry read(JsonObject jsonObject, JsonDeserializationContext context) throws JsonParseException {
        String type = GsonHelper.getAsString(jsonObject, "type", "connecting");
        if (!"connecting".equals(type)) {
            throw new JsonParseException("Unsupported zps:model type: " + type);
        }
        List<ConnectionRule> rules = ConnectionRule.parse(
                jsonObject.has("connections") ? GsonHelper.getAsJsonArray(jsonObject, "connections") : null);
        Set<String> uvLockTextures = readUvLockTextures(jsonObject);

        JsonObject stripped = jsonObject.deepCopy();
        stripped.remove("loader");
        stripped.remove("type");
        stripped.remove("connections");
        stripped.remove("uvlock_textures");

        if (uvLockTextures.isEmpty()) {
            return new ConnectedGeometry(BlockModel.fromString(stripped.toString()), null, rules);
        }
        if (!stripped.has("elements")) {
            throw new JsonParseException("uvlock_textures requires the model to declare its own elements");
        }

        JsonArray elements = GsonHelper.getAsJsonArray(stripped, "elements");
        JsonArray freeElements = new JsonArray();
        JsonArray lockedElements = new JsonArray();
        for (JsonElement element : elements) {
            JsonObject free = element.getAsJsonObject().deepCopy();
            JsonObject locked = element.getAsJsonObject().deepCopy();
            JsonObject freeFaces = new JsonObject();
            JsonObject lockedFaces = new JsonObject();
            for (Map.Entry<String, JsonElement> face : GsonHelper.getAsJsonObject(free, "faces").entrySet()) {
                String texture = GsonHelper.getAsString(face.getValue().getAsJsonObject(), "texture");
                (uvLockTextures.contains(stripHash(texture)) ? lockedFaces : freeFaces).add(face.getKey(), face.getValue());
            }
            if (freeFaces.size() > 0) {
                free.add("faces", freeFaces);
                freeElements.add(free);
            }
            if (lockedFaces.size() > 0) {
                locked.add("faces", lockedFaces);
                lockedElements.add(locked);
            }
        }

        BlockModel free = withElements(stripped, freeElements);
        BlockModel locked = lockedElements.isEmpty() ? null : withElements(stripped, lockedElements);
        return new ConnectedGeometry(free, locked, rules);
    }

    private static Set<String> readUvLockTextures(JsonObject jsonObject) {
        Set<String> textures = new HashSet<>();
        if (!jsonObject.has("uvlock_textures")) {
            return textures;
        }
        for (JsonElement element : GsonHelper.getAsJsonArray(jsonObject, "uvlock_textures")) {
            textures.add(stripHash(GsonHelper.convertToString(element, "uvlock_textures entry")));
        }
        return textures;
    }

    private static String stripHash(String texture) {
        return texture.startsWith("#") ? texture.substring(1) : texture;
    }

    private static BlockModel withElements(JsonObject model, JsonArray elements) {
        JsonObject copy = model.deepCopy();
        copy.add("elements", elements);
        return BlockModel.fromString(copy.toString());
    }
}
