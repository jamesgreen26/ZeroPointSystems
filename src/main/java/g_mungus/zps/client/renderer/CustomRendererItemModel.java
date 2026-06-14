package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.BakedModelWrapper;

public class CustomRendererItemModel extends BakedModelWrapper<BakedModel> {
    public CustomRendererItemModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHand) {
        super.applyTransform(displayContext, poseStack, leftHand);
        return this;
    }
}
