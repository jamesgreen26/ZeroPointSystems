package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphereTemperature;
import g_mungus.zps.world.AtmosphericBiome;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives each biome its {@link AtmosphericComposition}, and skips vanilla's falloff above y=80 for the
 * atmosphere temperature modifier, which is the whole height curve.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin implements AtmosphericBiome {
    @Shadow @Final private Biome.ClimateSettings climateSettings;

    @Unique
    private AtmosphericComposition zps$atmosphericComposition = AtmosphericComposition.DEFAULT;

    @Shadow public abstract float getBaseTemperature();

    @Inject(method = "getHeightAdjustedTemperature", at = @At("HEAD"), cancellable = true)
    private void zps$atmosphereTemperature(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (climateSettings.temperatureModifier() == AtmosphereTemperature.MODIFIER) {
            cir.setReturnValue(AtmosphereTemperature.MODIFIER.modifyTemperature(pos, getBaseTemperature()));
        }
    }

    @Override
    public AtmosphericComposition zps$getAtmosphericComposition() {
        return zps$atmosphericComposition;
    }

    @Override
    public void zps$setAtmosphericComposition(AtmosphericComposition composition) {
        zps$atmosphericComposition = composition;
    }
}
