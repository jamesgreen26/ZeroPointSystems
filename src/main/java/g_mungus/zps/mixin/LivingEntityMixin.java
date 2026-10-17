package g_mungus.zps.mixin;

import g_mungus.zps.gas.AtmosphereBreather;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.item.CustomArmPoseItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements AtmosphereBreather {

    @Unique
    private long zps$drowningTick = Long.MIN_VALUE;
    @Unique
    private boolean zps$syncedDrowning;

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void zps$preSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {

        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack itemInHand = self.getItemInHand(hand);

        if (itemInHand.getItem() instanceof CustomArmPoseItem item && !item.shouldSwingArm()) {
            ci.cancel();
        }
    }

    /** Ahead of the breathing check later in this method, so drowning in gas lands the same tick. */
    @Inject(method = "baseTick", at = @At("HEAD"))
    private void zps$atmosphere(CallbackInfo ci) {
        AtmosphereExposure.tick((LivingEntity) (Object) this);
    }

    @Override
    public long zps$getDrowningTick() {
        return zps$drowningTick;
    }

    @Override
    public void zps$setDrowningTick(long gameTime) {
        zps$drowningTick = gameTime;
    }

    @Override
    public boolean zps$isSyncedDrowning() {
        return zps$syncedDrowning;
    }

    @Override
    public void zps$setSyncedDrowning(boolean drowning) {
        zps$syncedDrowning = drowning;
    }
}
