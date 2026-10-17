package g_mungus.zps.client.model.connected;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import g_mungus.zps.mixin.SpriteSourcesInvoker;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Adds each tile of every pieced strip to the atlas as a sprite of its own. Connecting faces draw from these
 * rather than from the strip, so a quad's sprite always spans exactly one block's worth of texture — which is
 * what other mods that crop or re-map quads (Copycats+, Create's copycats) assume when they recompute UVs.
 */
public final class PiecedTileSource implements SpriteSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final PiecedTileSource INSTANCE = new PiecedTileSource();
    public static final SpriteSourceType TYPE = SpriteSourcesInvoker.zps$register("zps:pieced_tiles", Codec.unit(INSTANCE));

    private PiecedTileSource() {}

    /** The sprite holding one tile of a pieced strip. */
    public static ResourceLocation tile(ResourceLocation strip, int index) {
        return strip.withSuffix("_pieced_" + index);
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        // Listed by texture: the resource manager never lists .mcmeta files themselves, only attaches them.
        for (Map.Entry<ResourceLocation, Resource> entry : TEXTURE_ID_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            boolean pieced = ConnectedTextureMeta.read(entry.getValue()).map(ConnectedTextureMeta.Meta::pieced).orElse(false);
            if (!pieced) {
                continue;
            }
            ResourceLocation strip = TEXTURE_ID_CONVERTER.fileToId(entry.getKey());
            LazyLoadedImage image = new LazyLoadedImage(entry.getKey(), entry.getValue(), PiecedLayout.TILE_COUNT);
            for (int i = 0; i < PiecedLayout.TILE_COUNT; i++) {
                output.add(tile(strip, i), new Tile(image, tile(strip, i), i));
            }
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    /** One square tile cut from a strip; tiles are as wide as the strip is tall. */
    private record Tile(LazyLoadedImage image, ResourceLocation name, int index) implements SpriteSupplier {
        @Override
        public SpriteContents get() {
            try {
                NativeImage strip = image.get();
                int size = strip.getHeight();
                NativeImage tile = new NativeImage(NativeImage.Format.RGBA, size, size, false);
                strip.copyRect(tile, index * size, 0, 0, 0, size, size, false, false);
                return new SpriteContents(name, new FrameSize(size, size), tile, net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.EMPTY);
            } catch (Exception e) {
                LOGGER.error("Failed to cut pieced tile {}", name, e);
            } finally {
                image.release();
            }
            return MissingTextureAtlasSprite.create();
        }

        @Override
        public void discard() {
            image.release();
        }
    }
}
