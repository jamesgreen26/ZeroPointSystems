package g_mungus.zps.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.zps.client.ponder.ZPSPonderTagScreen;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ScreenOpener.class, remap = false)
public class ScreenOpenerMixin {
    @WrapMethod(method = "transitionTo")
    private static void zps$replacePonderTagScreen(NavigatableSimiScreen screen, Operation<Void> original) {
        if (screen instanceof PonderTagScreen tagScreen) {
            original.call(new ZPSPonderTagScreen(tagScreen.getTag()));
            return;
        }

        original.call(screen);
    }
}
