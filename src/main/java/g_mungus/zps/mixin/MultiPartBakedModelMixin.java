package g_mungus.zps.mixin;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.IForgeBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.MultipartModelData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

@Pseudo
@Mixin(value = MultiPartBakedModel.class, priority = 900)
public class MultiPartBakedModelMixin implements IForgeBakedModel {
    @Shadow
    @Final
    private List<Pair<Predicate<BlockState>, BakedModel>> selectors;

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        MultipartModelData.Builder builder = null;
        BitSet selectedModels = ((MultiPartBakedModel) (Object) this).getSelectors(state);
        for (int i = 0; i < selectedModels.length(); i++) {
            if (!selectedModels.get(i)) {
                continue;
            }
            BakedModel model = selectors.get(i).getRight();
            ModelData childData = model.getModelData(level, pos, state, modelData);
            if (childData == modelData) {
                continue;
            }
            if (builder == null) {
                builder = MultipartModelData.builder();
            }
            builder.with(model, childData);
        }
        return builder == null
                ? modelData
                : modelData.derive().with(MultipartModelData.PROPERTY, builder.build()).build();
    }
}
