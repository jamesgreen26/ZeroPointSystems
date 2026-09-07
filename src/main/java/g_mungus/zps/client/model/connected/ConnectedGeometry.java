package g_mungus.zps.client.model.connected;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ConnectedGeometry implements IUnbakedGeometry<ConnectedGeometry> {
    private final BlockModel inner;
    /** Faces listed under {@code uvlock_textures}, baked with UV lock regardless of the blockstate. May be null. */
    @Nullable
    private final BlockModel uvLocked;
    private final List<ConnectionRule> rules;

    public ConnectedGeometry(BlockModel inner, @Nullable BlockModel uvLocked, List<ConnectionRule> rules) {
        this.inner = inner;
        this.uvLocked = uvLocked;
        this.rules = rules;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        inner.resolveParents(modelGetter);
        if (uvLocked != null) {
            uvLocked.resolveParents(modelGetter);
        }
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        BakedModel baked = inner.bake(baker, spriteGetter, modelState);
        if (uvLocked != null) {
            BakedModel locked = uvLocked.bake(baker, spriteGetter, new SimpleModelState(modelState.getRotation(), true));
            baked = new Merged(baked, locked);
        }
        RandomSource rand = RandomSource.create(42L);
        ResourceLocation renderTypeHint = context.getRenderTypeHint();
        RenderTypeGroup renderTypes = renderTypeHint == null ? RenderTypeGroup.EMPTY : context.getRenderType(renderTypeHint);
        return new ConnectingBakedModel(baked, rules, null, rand, renderTypes);
    }

    /** Presents the free and UV-locked bakes of one model as a single quad source. */
    private static final class Merged extends BakedModelWrapper<BakedModel> {
        private final BakedModel locked;

        Merged(BakedModel free, BakedModel locked) {
            super(free);
            this.locked = locked;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return concat(originalModel.getQuads(state, side, rand), locked.getQuads(state, side, rand));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                        ModelData data, @Nullable RenderType renderType) {
            return concat(originalModel.getQuads(state, side, rand, data, renderType),
                    locked.getQuads(state, side, rand, data, renderType));
        }

        private static List<BakedQuad> concat(List<BakedQuad> a, List<BakedQuad> b) {
            List<BakedQuad> out = new ArrayList<>(a.size() + b.size());
            out.addAll(a);
            out.addAll(b);
            return out;
        }
    }
}
