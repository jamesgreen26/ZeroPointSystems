package g_mungus.zps.mixin;

import g_mungus.zps.world.SkyBiomeSettings;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sky bands where no chunk is loaded: {@code getBiome} falls back to the generator's biome source
 * there, which knows nothing of {@link SkyBiomeSettings}. The client's fallback is {@code ClientLevelMixin}'s.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void zps$skyBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = SkyBiomeSettings.skyBiome((ServerLevel) (Object) this, y);
        if (biome != null) cir.setReturnValue(biome);
    }
}
