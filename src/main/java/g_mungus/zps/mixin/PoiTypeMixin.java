package g_mungus.zps.mixin;

import g_mungus.zps.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dirt-filled composter is still a farmer's workstation. {@code PoiType} is a record whose
 * hash covers its block states, and Forge's registry keeps entries in a hash bimap, so the
 * farmer's state set cannot be widened after registration; the membership test is extended
 * here instead. The state-to-type map that block changes go through is filled from
 * {@link g_mungus.zps.ZPSMod} at setup.
 */
@Mixin(PoiType.class)
public class PoiTypeMixin {

    @Inject(method = "is", at = @At("HEAD"), cancellable = true)
    private void zps$composterDirtIsFarmerWork(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModBlocks.COMPOSTER_DIRT.get())
                && (Object) this == BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(PoiTypes.FARMER)) {
            cir.setReturnValue(true);
        }
    }
}
