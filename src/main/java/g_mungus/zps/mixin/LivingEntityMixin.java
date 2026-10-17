package g_mungus.zps.mixin;

import g_mungus.zps.gas.AtmosphereBreather;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.gas.api.GasEnvironmentModifier;
import g_mungus.zps.item.CustomArmPoseItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements AtmosphereBreather {

    @Unique
    private long zps$drowningTick = Long.MIN_VALUE;
    @Unique
    private boolean zps$syncedDrowning;
    @Unique
    private final List<GasEnvironmentModifier> zps$queuedModifiers = new ArrayList<>(0);
    @Unique
    private long zps$queuedTick = Long.MIN_VALUE;
    @Unique
    private @Nullable GasConditions zps$gasConditions;

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

    @Override
    public List<GasEnvironmentModifier> zps$getQueuedModifiers() {
        return zps$queuedModifiers;
    }

    @Override
    public long zps$getQueuedTick() {
        return zps$queuedTick;
    }

    @Override
    public void zps$setQueuedTick(long gameTime) {
        zps$queuedTick = gameTime;
    }

    @Override
    public @Nullable GasConditions zps$getGasConditions() {
        return zps$gasConditions;
    }

    @Override
    public void zps$setGasConditions(@Nullable GasConditions conditions) {
        zps$gasConditions = conditions;
    }
}
