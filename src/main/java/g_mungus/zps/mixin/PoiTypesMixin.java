package g_mungus.zps.mixin;

import g_mungus.zps.block.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * A dirt-filled composter is a farmer's workstation, the same point of interest as the vanilla
 * composter, so the swap between the two never touches the PoI record and a farmer keeps its
 * claim, profession and trades across both conversions.
 *
 * <p>Forge rebuilds the block state to type map from each type's own state set on every registry
 * sync, and that set cannot be widened after registration (the type is a record keyed by hash in
 * the registry), so the lookups are answered here instead. {@link PoiTypeMixin} extends the
 * type's membership test to match.
 */
@Mixin(PoiTypes.class)
public class PoiTypesMixin {

    @Inject(method = "forState", at = @At("HEAD"), cancellable = true)
    private static void zps$composterDirtIsFarmerWork(BlockState state, CallbackInfoReturnable<Optional<Holder<PoiType>>> cir) {
        if (state.is(ModBlocks.COMPOSTER_DIRT.get())) {
            cir.setReturnValue(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolder(PoiTypes.FARMER).map(holder -> holder));
        }
    }

    @Inject(method = "hasPoi", at = @At("HEAD"), cancellable = true)
    private static void zps$composterDirtHasPoi(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModBlocks.COMPOSTER_DIRT.get())) {
            cir.setReturnValue(true);
        }
    }
}
