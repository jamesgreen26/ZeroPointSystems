package g_mungus.zps.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NavigatableSimiScreen.class, remap = false)
public class NavigatableSimiScreenMixin {


    @SuppressWarnings("ConstantConditions")
    @WrapOperation(
            method = "renderWindow",
            remap = false,
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/gui/UIRenderHelper;breadcrumbArrow(Lnet/minecraft/client/gui/GuiGraphics;IIIIIILnet/createmod/catnip/data/Couple;)V")
    )
    public void injectTranslate(GuiGraphics graphics, int x, int y, int z, int width, int height, int indent, Couple<Color> colors, Operation<Void> original) {
        if (!PonderUI.class.isInstance(this)) {
            original.call(graphics, x, y, z, width, height, indent, colors);
        }
    }
}
