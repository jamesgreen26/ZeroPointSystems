package g_mungus.zps.mixin;

import g_mungus.zps.entity.StandingVehicleEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    public PlayerModelMixin(final ModelPart model) {
        super(model);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "HEAD"))
    public void setupAnim(final T livingEntity, final float swing, final float g, final float tick, final float i, final float j, final CallbackInfo info) {
        if (livingEntity.getVehicle() instanceof StandingVehicleEntity) {
            this.riding = false; // Do not sit
        }
    }
}
