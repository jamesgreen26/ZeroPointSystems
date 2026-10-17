package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphereTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The atmosphere modifier is the whole height curve, so vanilla's falloff above y=80 is skipped for it. */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Shadow @Final private Biome.ClimateSettings climateSettings;

    @Shadow public abstract float getBaseTemperature();

    @Inject(method = "getHeightAdjustedTemperature", at = @At("HEAD"), cancellable = true)
    private void zps$atmosphereTemperature(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (climateSettings.temperatureModifier() == AtmosphereTemperature.MODIFIER) {
            cir.setReturnValue(AtmosphereTemperature.MODIFIER.modifyTemperature(pos, getBaseTemperature()));
        }
    }
}
