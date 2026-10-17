package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The server's load from datapacks and the client's load from the network both end in this
 * method, so each fresh set of biomes is handed its {@link AtmosphericComposition} here.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {

    @Inject(method = "load(Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;",
            at = @At("RETURN"))
    private static void zps$attachAtmosphericComposition(CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        AtmosphericComposition.attachToBiomes(cir.getReturnValue());
    }
}
