package g_mungus.zps.compat;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.model.connected.ConnectingBakedModel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Clutter No More adds a vertical slab for every slab and a step for every stairs. It builds their models from
 * the source model's textures alone, dropping our connecting loader, so a connecting texture's whole strip got
 * squashed onto each face. Each shape's baked models are wrapped in the connecting model of the block it was
 * made from, and its rules see the shape as that block, so shapes and the full blocks connect both ways.
 */
public final class ClutterNoMoreClientCompat {
    private static final String NAMESPACE = "clutternomore";
    /** Shapes of another mod's blocks sit under a folder named after it; vanilla ones have none. */
    private static final String PREFIX = ZPSMod.MOD_ID + "/";

    private ClutterNoMoreClientCompat() {}

    public static void wrapShapeModels(Map<ModelResourceLocation, BakedModel> models) {
        Map<Block, Block> aliases = new HashMap<>();
        for (Block shape : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(shape);
            Block source = sourceOf(id);
            if (source == null) {
                continue;
            }
            if (!(models.get(BlockModelShaper.stateToModelLocation(source.defaultBlockState()))
                    instanceof ConnectingBakedModel connecting)) {
                continue;
            }
            aliases.put(shape, source);
            for (BlockState state : shape.getStateDefinition().getPossibleStates()) {
                wrap(models, BlockModelShaper.stateToModelLocation(state), connecting);
            }
            wrap(models, ModelResourceLocation.inventory(id), connecting);
        }
        ConnectingBakedModel.setAliases(aliases);
    }

    private static void wrap(Map<ModelResourceLocation, BakedModel> models, ModelResourceLocation location,
                             ConnectingBakedModel connecting) {
        BakedModel model = models.get(location);
        BakedModel wrapped = model == null ? null : connecting.connect(model);
        if (wrapped != null) {
            models.put(location, wrapped);
        }
    }

    /**
     * The block a shape was made from, read off its name: {@code zps/vertical_x_slab} from {@code zps:x_slab},
     * {@code zps/x_step} from {@code zps:x_stairs}. Null for anything else.
     */
    private static @Nullable Block sourceOf(ResourceLocation shape) {
        if (!shape.getNamespace().equals(NAMESPACE) || !shape.getPath().startsWith(PREFIX)) {
            return null;
        }
        String path = shape.getPath().substring(PREFIX.length());
        String source;
        if (path.startsWith("vertical_") && path.endsWith("_slab")) {
            source = path.substring("vertical_".length());
        } else if (path.endsWith("_step")) {
            source = path.substring(0, path.length() - "_step".length()) + "_stairs";
        } else {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(ZPSMod.resource(source));
        return block == Blocks.AIR ? null : block;
    }
}
