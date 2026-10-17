package g_mungus.zps.mixin;

import g_mungus.zps.world.SkyBiomeSettings;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@link SkyBiomeSettings}: a height above the build limit that falls in one of the
 * dimension's bands reads that band's biome, instead of vanilla's clamp to the top section. Chunks
 * hold their level as the height accessor; on the client too, since the registry is synced.
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @Inject(method = "getNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void zps$skyBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!(levelHeightAccessor instanceof Level level)) return;
        Holder<Biome> biome = SkyBiomeSettings.skyBiome(level, y);
        if (biome != null) cir.setReturnValue(biome);
    }
}
