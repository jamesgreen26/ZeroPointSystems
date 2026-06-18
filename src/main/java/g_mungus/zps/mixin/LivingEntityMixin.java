package g_mungus.zps.mixin;

import g_mungus.zps.item.CustomArmPoseItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void zps$preSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {

        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack itemInHand = self.getItemInHand(hand);

        if (itemInHand.getItem() instanceof CustomArmPoseItem item && !item.shouldSwingArm()) {
            ci.cancel();
        }
    }
}
