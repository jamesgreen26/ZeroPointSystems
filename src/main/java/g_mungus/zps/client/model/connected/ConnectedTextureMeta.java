package g_mungus.zps.client.model.connected;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectedTextureMeta {
    public record Meta(boolean pieced) {}

    private static final Map<ResourceLocation, Optional<Meta>> CACHE = new ConcurrentHashMap<>();

    private ConnectedTextureMeta() {}

    /** Clears the cache; call on resource reload. */
    public static void clear() {
        CACHE.clear();
    }

    /** Returns the metadata for a texture (e.g. {@code zps:block/decor/space_grating_block}), if any. */
    public static Optional<Meta> get(ResourceLocation textureId) {
        return CACHE.computeIfAbsent(textureId, ConnectedTextureMeta::load);
    }

    private static Optional<Meta> load(ResourceLocation textureId) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        ResourceLocation mcmeta = ResourceLocation.fromNamespaceAndPath(
                textureId.getNamespace(), "textures/" + textureId.getPath() + ".png.mcmeta");
        Optional<Resource> resource = rm.getResource(mcmeta);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = GsonHelper.parse(reader);
            if (!root.has("zps")) {
                return Optional.empty();
            }
            JsonObject zps = GsonHelper.getAsJsonObject(root, "zps");
            if (!"connecting".equals(GsonHelper.getAsString(zps, "type", ""))) {
                return Optional.empty();
            }
            boolean pieced = "pieced".equals(GsonHelper.getAsString(zps, "layout", ""));
            return Optional.of(new Meta(pieced));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
