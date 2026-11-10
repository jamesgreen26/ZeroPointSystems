package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.entity.ControllerSeatEntity;
import g_mungus.zps.gas.GasExposure;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneaking does nothing on a controller: shift may well be one of its keys. It neither steps the
 * rider off — using the controller again does that — nor crouches them, so the camera, hitbox and
 * model stay put. {@code isShiftKeyDown} itself still reports the key. A turtle helmet also counts
 * drowning in gas as being under water.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void zps$stayOnController(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof ControllerSeatEntity) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
            method = "updatePlayerPose",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z"))
    private boolean zps$noCrouchOnController(Player player, Operation<Boolean> original) {
        return !(player.getVehicle() instanceof ControllerSeatEntity) && original.call(player);
    }

    /**
     * A turtle helmet tops up water breathing whenever its wearer's head is out of water, which in
     * unbreathable gas would be every tick. Count drowning in gas as being under water, so the helmet
     * gives the same few extra seconds there as it does in the sea.
     */
    @WrapOperation(
            method = "turtleHelmetTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean zps$turtleHelmetHoldsInGas(Player player, TagKey<Fluid> fluid, Operation<Boolean> original) {
        return original.call(player, fluid) || GasExposure.isDrowningOnEitherSide(player);
    }
}
