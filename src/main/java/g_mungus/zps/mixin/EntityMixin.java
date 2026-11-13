package g_mungus.zps.mixin;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.entity.EasedSync;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the position the server reports to an {@link EasedSync} entity instead of putting the entity there.
 *
 * <p>{@code lerpTo} is what the client's packet handlers call with it. Despite the name and the step count it is
 * given, the base version moves the entity at once; mobs, boats and minecarts override it with a real one. It is
 * caught here rather than overridden in the falling block, which would clash with any other mod doing the same.
 *
 * <p>Also keeps an entity in a room on a moving grid carried by that grid; see {@link Compat#carryWithRoom}.
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void zps$easeInstead(double x, double y, double z, float yRot, float xRot, int steps, CallbackInfo ci) {
        if ((Object) this instanceof EasedSync eased && eased.zps$easeToward(x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void zps$carryWithRoom(CallbackInfo ci) {
        Compat.carryWithRoom((Entity) (Object) this);
    }
}
