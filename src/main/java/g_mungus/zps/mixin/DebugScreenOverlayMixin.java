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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F3 only shows the biome (and local difficulty) inside the build height. Above it the overworld has
 * a sky biome of its own ({@code SkyBiomeSettings}), so drop the bounds check and show it everywhere.
 * Just above the biome go the gas conditions the server last applied to the player — the atmosphere
 * as every source has changed it — or, until the server has answered, the atmosphere at the
 * camera's eyes as {@link AtmosphereExposure} reads it: one line for its temperature and whether it
 * can be breathed, and one for its gases, most first, each with its share.
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
                + (conditions.exposed() ? "" : " (shielded)"));
        lines.add("Gases: " + zps$displayedGases(conditions.gases()));
    }

    /**
     * Each gas with its share, most first. Vacuum is what is left with nothing else there: shown for
     * no gas at all, and hidden beside any, the others' shares then taken of what they add up to.
     */
    @Unique
    private static String zps$displayedGases(Map<ResourceLocation, Double> gases) {
        Map<ResourceLocation, Double> others = new HashMap<>(gases);
        others.remove(AtmosphericComposition.VACUUM);
        double total = others.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!(total > 0)) {
            return AtmosphericComposition.VACUUM.toString();
        }
        return others.entrySet().stream()
                .sorted(Map.Entry.<ResourceLocation, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(gas -> String.format(Locale.ROOT, "%s %.1f%%", gas.getKey(), 100 * gas.getValue() / total))
                .collect(Collectors.joining(", "));
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
        Map<ResourceLocation, Double> gases = composition.at(eyes.getY());
        return new GasConditions(AtmosphereExposure.temperatureK(biome, composition, eyes),
                AtmosphereExposure.isBreathable(level, composition, eyes.getY()), gases, true);
    }
}
