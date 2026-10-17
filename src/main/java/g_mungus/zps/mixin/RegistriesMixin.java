package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Points {@link AtmosphericComposition}'s registry at the biome folder, so it reads the biome files
 * themselves. Both the server's load and the client's load from its own packs resolve the folder here.
 */
@Mixin(Registries.class)
public abstract class RegistriesMixin {

    @Inject(method = "elementsDirPath", at = @At("HEAD"), cancellable = true)
    private static void zps$atmosphereFromBiomes(ResourceKey<? extends Registry<?>> key, CallbackInfoReturnable<String> cir) {
        if (key.equals(AtmosphericComposition.REGISTRY_KEY)) {
            cir.setReturnValue(Registries.elementsDirPath(Registries.BIOME));
        }
    }
}
