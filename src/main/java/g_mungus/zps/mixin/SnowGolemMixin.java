package g_mungus.zps.mixin;

import g_mungus.zps.block.PowderSnowCauldrons;
import net.minecraft.world.entity.animal.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the snow golem's snow trail to cauldrons: one it stands in or on gains a layer of
 * powder snow each tick. See {@link PowderSnowCauldrons#snowGolemTrail}.
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void zps$fillCauldronsUnderfoot(CallbackInfo ci) {
        PowderSnowCauldrons.snowGolemTrail((SnowGolem) (Object) this);
    }
}
