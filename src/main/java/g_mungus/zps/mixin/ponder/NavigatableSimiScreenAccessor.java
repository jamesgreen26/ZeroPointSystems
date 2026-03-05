package g_mungus.zps.mixin.ponder;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.widget.BoxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NavigatableSimiScreen.class, remap = false)
public interface NavigatableSimiScreenAccessor {
    @Accessor(remap = false)
    LerpedFloat getArrowAnimation();

    @Accessor(remap = false)
    BoxWidget getBackTrack();
}
