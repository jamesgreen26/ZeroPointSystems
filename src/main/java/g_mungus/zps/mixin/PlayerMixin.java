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

/**
 * Sneaking never crouches a rider on a controller, so the camera, hitbox and model stay put.
 * {@code isShiftKeyDown} itself still reports the key, and it steps them off as on any vehicle —
 * unless the sneak key is one of the controller's own, in which case the rider's client shadows it
 * and never reports it (see {@code ControllerClientHooks}). A turtle helmet also counts drowning in
 * gas as being under water.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

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
