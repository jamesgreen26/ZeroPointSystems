package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphereTemperature;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code NONE}'s body class, shared by the atmosphere constant: see {@code TemperatureModifierMixin}. */
@Mixin(targets = "net.minecraft.world.level.biome.Biome$TemperatureModifier$1")
public abstract class TemperatureModifierNoneMixin {

    @Inject(method = "modifyTemperature", at = @At("HEAD"), cancellable = true)
    private void zps$atmosphere(BlockPos pos, float temperature, CallbackInfoReturnable<Float> cir) {
        if ((Object) this == AtmosphereTemperature.MODIFIER) {
            cir.setReturnValue(AtmosphereTemperature.temperature(pos.getY(), temperature));
        }
    }
}
