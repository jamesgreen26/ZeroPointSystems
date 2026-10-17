package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3 only shows the biome (and local difficulty) inside the build height. Above it the overworld has
 * a sky biome of its own ({@code SkyBiomeSettings}), so drop the bounds check and show it everywhere.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @ModifyExpressionValue(method = "getGameInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinBuildHeight()I"))
    private int zps$noMinBiomeHeight(int minBuildHeight) {
        return Integer.MIN_VALUE;
    }

    @ModifyExpressionValue(method = "getGameInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxBuildHeight()I"))
    private int zps$noMaxBiomeHeight(int maxBuildHeight) {
        return Integer.MAX_VALUE;
    }
}
