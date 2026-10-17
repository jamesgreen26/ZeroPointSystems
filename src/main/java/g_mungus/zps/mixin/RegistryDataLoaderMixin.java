package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Points {@link AtmosphericComposition}'s registry at the biome folder, so it reads the biome files
 * themselves, and hands each fresh set of biomes loaded from datapacks its composition. On 1.20.1 the
 * client gets its registries in the login packet instead; see {@code ClientPacketListenerMixin}.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {

    @Inject(method = "registryDirPath", at = @At("HEAD"), cancellable = true)
    private static void zps$atmosphereFromBiomes(ResourceLocation registry, CallbackInfoReturnable<String> cir) {
        if (registry.equals(AtmosphericComposition.REGISTRY_KEY.location())) {
            cir.setReturnValue("worldgen/biome");
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void zps$attachAtmosphericComposition(CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        AtmosphericComposition.attachToBiomes(cir.getReturnValue());
    }
}
