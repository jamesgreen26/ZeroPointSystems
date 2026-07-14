package g_mungus.zps.mixin;

import g_mungus.zps.client.screens.GhostSlotRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives {@link GhostSlotRenderer} screens a chance to draw a slot's contents themselves before vanilla
 * renders it. This is the Forge 1.20.1 stand-in for the per-slot {@code renderSlotContents} hook that
 * later versions expose, used by the assembler to render its pattern cells as translucent ghost previews.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void zps$renderGhostSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (this instanceof GhostSlotRenderer ghostRenderer && ghostRenderer.zps$renderGhostSlot(graphics, slot)) {
            ci.cancel();
        }
    }
}
