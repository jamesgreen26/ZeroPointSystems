package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.zps.client.ClientAtmosphere;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The stars come out as the air thins, by the {@link AtmosphericComposition#getDensity density} at the
 * camera, however bright the day, up to the brightest vanilla gives them at midnight. (The weather
 * clearing from the sky is the rain level's own, in {@code ClientLevelMixin}.)
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    /** {@code getStarBrightness} at midnight: as bright as vanilla ever draws the stars. */
    @Unique
    private static final float ZPS$NIGHT_STAR_BRIGHTNESS = 0.5f;

    /**
     * Applied after vanilla has faded the stars by the weather, as a floor, so the stars that come out
     * of thin air are never faded themselves.
     */
    @Definition(id = "getStarBrightness", method = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F")
    @Expression("?.getStarBrightness(?) * ?")
    @ModifyExpressionValue(method = "renderSky", at = @At("MIXINEXTRAS:EXPRESSION"))
    private float zps$starsThroughThinAir(float brightness) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? brightness : Math.max(brightness, ZPS$NIGHT_STAR_BRIGHTNESS * (1.0f - ClientAtmosphere.cameraDensity(level)));
    }
}
