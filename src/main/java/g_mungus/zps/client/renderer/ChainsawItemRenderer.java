package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;

public class ChainsawItemRenderer extends PoweredToolItemRenderer {
    public static final ModelResourceLocation BLADE_MODEL = ModelResourceLocation.standalone(ZPSMod.resource("item/chainsaw_blade"));

    public ChainsawItemRenderer() {
        super(BASE_MODEL, BLADE_MODEL);
    }

    @Override
    protected void applyHeadTransform(PoseStack poseStack, float spinAngle, float boostProgress) {
        float chainRattle = Mth.sin(spinAngle * Mth.DEG_TO_RAD * 10) * (boostProgress * 0.025F);
        poseStack.translate(0.0F, 0.0F, chainRattle);
    }
}
