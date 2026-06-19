package g_mungus.zps.client.model.connected;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.List;
import java.util.function.Function;

public class ConnectedGeometry implements IUnbakedGeometry<ConnectedGeometry> {
    private final BlockModel inner;
    private final List<ConnectionRule> rules;

    public ConnectedGeometry(BlockModel inner, List<ConnectionRule> rules) {
        this.inner = inner;
        this.rules = rules;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        inner.resolveParents(modelGetter);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        BakedModel baked = inner.bake(baker, spriteGetter, modelState);
        RandomSource rand = RandomSource.create(42L);
        ResourceLocation renderTypeHint = context.getRenderTypeHint();
        RenderTypeGroup renderTypes = renderTypeHint == null ? RenderTypeGroup.EMPTY : context.getRenderType(renderTypeHint);
        return new ConnectingBakedModel(baked, rules, null, rand, renderTypes);
    }
}
