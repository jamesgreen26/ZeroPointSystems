package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.zps.client.ClientAtmosphere;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The sunrise and sunset glow darkens with the air, as the sky does ({@code ClientLevelMixin}), by the
 * {@link AtmosphericComposition#getDensity density} at the camera. The returned array is vanilla's
 * own, reused each call, so the scaled colour goes out in a copy; alpha is left as it was.
 */
@Mixin(DimensionSpecialEffects.class)
public abstract class DimensionSpecialEffectsMixin {

    @ModifyReturnValue(method = "getSunriseColor", at = @At("RETURN"))
    private float[] zps$thinSunrise(float[] colour) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (colour == null || level == null) {
            return colour;
        }
        float density = ClientAtmosphere.cameraDensity(level);
        if (density >= 1.0f) {
            return colour;
        }
        return new float[] {colour[0] * density, colour[1] * density, colour[2] * density, colour[3]};
    }
}
