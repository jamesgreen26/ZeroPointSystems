package g_mungus.zps.mixin.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour", remap = false)
public interface ScrollValueBehaviourAccessor {
    @Accessor
    int getMax();

    @Accessor
    int getMin();
}
