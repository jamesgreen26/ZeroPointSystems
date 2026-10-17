package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.QuartPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fog darkens with the air, like the sky ({@code ClientLevelMixin}) and the sunrise glow
 * ({@code DimensionSpecialEffectsMixin}). Fog colour is the biomes' fog colours sampled around the
 * camera, then blended towards the sky colour and the sunrise glow; those two already carry the
 * density, so it goes on each biome sample here, at the sample's own height, and the blend comes out
 * scaled once. Fog in water, lava and the like is worked out elsewhere and is untouched.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @WrapOperation(method = "lambda$setupColor$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getFogColor()I"))
    private static int zps$thinFog(Biome biome, Operation<Integer> original,
                                   ClientLevel level, BiomeManager biomes, float brightness, int x, int y, int z) {
        int colour = original.call(biome);
        float density = AtmosphericComposition.of(biome).density(QuartPos.toBlock(y), level.getMaxBuildHeight());
        if (density >= 1.0f) {
            return colour;
        }
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(colour),
                Math.round(FastColor.ARGB32.red(colour) * density),
                Math.round(FastColor.ARGB32.green(colour) * density),
                Math.round(FastColor.ARGB32.blue(colour) * density));
    }
}
