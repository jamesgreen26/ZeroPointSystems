package g_mungus.zps.mixin.ponder;

import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.utility.Couple;
import net.createmod.catnip.utility.theme.Color;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PonderUI.class, remap = false)
public class PonderUIMixin {

    @Inject(
            method = "renderSceneInformation",
            remap = false,
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/gui/element/BoxElement;<init>()V",
                    ordinal = 0
            )
    )
    private void beforeBoxElementInit(GuiGraphics graphics, float fade, float indexDiff, PonderScene activeScene, int tooltipColor, CallbackInfo ci) {
        graphics.pose().translate(0,0, 10f);
    }

    @Inject(method = "renderWindow", at = @At("TAIL"))
    public void renderWindowInject(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        zps$renderBackTrackArrows(graphics, partialTicks);
        graphics.pose().translate(0, 0, 400f);
    }

    @Unique
    private void zps$renderBackTrackArrows(GuiGraphics graphics, float partialTicks) {
        PonderUI ponderUI = (PonderUI)(Object)this;
        NavigatableSimiScreenAccessor accessor = (NavigatableSimiScreenAccessor) ponderUI;

        BoxWidget backTrack = accessor.getBackTrack();
        Couple<Color> colors = NavigatableSimiScreen.COLOR_NAV_ARROW;

        int x = (int) Mth.lerp((accessor.getArrowAnimation().getValue(partialTicks)), -9, 21);
        int maxX = backTrack.getX() + backTrack.getWidth();
        int height = ponderUI.height;

        if (x + 30 < backTrack.getX())
            UIRenderHelper.breadcrumbArrow(graphics, x + 30, height - 51, 0, maxX - (x + 30), 20, 5, colors);

        UIRenderHelper.breadcrumbArrow(graphics, x, height - 51, 0, 30, 20, 5, colors);
        UIRenderHelper.breadcrumbArrow(graphics, x - 30, height - 51, 0, 30, 20, 5, colors);
    }
}
