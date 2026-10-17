package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.client.ClientAtmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fog darkens with the air, like the sky ({@code ClientLevelMixin}) and the sunrise glow
 * ({@code DimensionSpecialEffectsMixin}), by the {@link g_mungus.zps.world.AtmosphericComposition#getDensity density}
 * at the camera. Fog colour is the biomes' fog colours sampled around the camera; this darkens what that
 * sampling gives back. The sky colour and sunrise glow it is then blended towards already carry the density,
 * so the blend comes out scaled once. Fog in water, lava and the like is worked out elsewhere and is untouched.
 * <p>
 * The sampling call is wrapped, not vanilla's sampler inside it: Sodium {@code @Redirect}s that call to a sampler
 * of its own, and MixinExtras applies this around the redirect, so the fog darkens the same with or without it.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 zps$thinFog(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original,
                                    Camera camera, float partialTick, ClientLevel level) {
        Vec3 colour = original.call(pos, fetcher);
        float density = ClientAtmosphere.cameraDensity(level);
        return density >= 1.0f ? colour : colour.scale(density);
    }
}
