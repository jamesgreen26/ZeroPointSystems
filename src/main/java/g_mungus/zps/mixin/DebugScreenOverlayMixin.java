package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.zps.client.debug.ClientGasConditions;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Locale;

/**
 * F3 only shows the biome (and local difficulty) inside the build height. Above it the overworld has
 * a sky biome of its own ({@code SkyBiomeSettings}), so drop the bounds check and show it everywhere.
 * Just above the biome go the gas conditions the server last applied to the player — the atmosphere
 * as every source has changed it — or, until the server has answered, the atmosphere at the
 * camera's eyes as {@link AtmosphereExposure} reads it.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

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

    @Inject(method = "getGameInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private void zps$atmosphereLine(CallbackInfoReturnable<List<String>> cir, @Local List<String> lines) {
        GasConditions conditions = ClientGasConditions.current();
        if (conditions == null) {
            conditions = zps$atmosphereAtCamera();
        }
        if (conditions == null) {
            return;
        }
        double kelvin = conditions.temperatureK();
        String temperature = Double.isNaN(kelvin) ? "n/a" : String.format(Locale.ROOT, "%.1f K", kelvin);
        lines.add("Temperature: " + temperature + ", Breathable: " + conditions.breathable()
                + ", Gasses: " + zps$displayedGases(conditions.gases()) + (conditions.exposed() ? "" : " (shielded)"));
    }

    /** Vacuum is what is left with nothing else there: shown for no gas at all, and hidden beside any. */
    @Unique
    private static List<ResourceLocation> zps$displayedGases(List<ResourceLocation> gases) {
        List<ResourceLocation> others = gases.stream().filter(gas -> !gas.equals(AtmosphericComposition.VACUUM)).toList();
        return others.isEmpty() ? List.of(AtmosphericComposition.VACUUM) : others;
    }

    @Unique
    private @Nullable GasConditions zps$atmosphereAtCamera() {
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (level == null || camera == null) {
            return null;
        }
        BlockPos eyes = BlockPos.containing(camera.getEyePosition());
        Biome biome = level.getBiome(eyes).value();
        AtmosphericComposition composition = AtmosphericComposition.of(biome);
        List<ResourceLocation> gases = composition.at(eyes.getY()).keySet().stream().sorted().toList();
        return new GasConditions(AtmosphereExposure.temperatureK(biome, composition, eyes),
                AtmosphereExposure.isBreathable(level, composition, eyes.getY()), gases, true);
    }
}
