package g_mungus.zps.mixin;

import g_mungus.zps.client.exhaust.ExhaustParticles;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the exhaust's tick last. Valkyrien Skies moves its ships on to the tick's pose at the tail of
 * the client tick, after the end-of-tick event; above its default priority, this is applied after
 * its mixin and so runs after it, with every grid's pose for the tick known.
 */
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class ExhaustTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void zps$exhaustAfterGrids(CallbackInfo ci) {
        ExhaustParticles.afterTick();
    }
}
