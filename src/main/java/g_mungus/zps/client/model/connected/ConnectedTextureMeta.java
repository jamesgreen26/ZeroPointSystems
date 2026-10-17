package g_mungus.zps.client.model.connected;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectedTextureMeta {
    public record Meta(boolean pieced) {}

    /** The {@code zps} section of a texture's {@code .png.mcmeta}; empty unless it is a connecting texture. */
    private static final MetadataSectionSerializer<Optional<Meta>> SECTION = new MetadataSectionSerializer<>() {
        @Override
        public String getMetadataSectionName() {
            return "zps";
        }

        @Override
        public Optional<Meta> fromJson(JsonObject zps) {
            if (!"connecting".equals(GsonHelper.getAsString(zps, "type", ""))) {
                return Optional.empty();
            }
            return Optional.of(new Meta("pieced".equals(GsonHelper.getAsString(zps, "layout", ""))));
        }
    };

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
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(file).flatMap(ConnectedTextureMeta::read);
    }

    /** Reads the metadata attached to a texture's {@code .png} resource. */
    public static Optional<Meta> read(Resource texture) {
        try {
            return texture.metadata().getSection(SECTION).flatMap(meta -> meta);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
