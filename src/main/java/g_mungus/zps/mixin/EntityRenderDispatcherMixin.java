package g_mungus.zps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a duct rider's shadow and debug hitbox off the screen while they are between vents.
 *
 * <p>The rider's own render is already cancelled for the crawl, but the shadow and the hitbox are
 * not the renderer's: the dispatcher draws both around it, so they were left showing at the far
 * vent, a mark on the floor and a box in the air saying exactly where someone was about to appear.
 * Both are skipped for the rider and for the duct vehicle carrying them, for as long as the rider
 * is not drawn.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void zps$noShadowInTheDucts(PoseStack poseStack, MultiBufferSource buffers, Entity entity,
                                               float weight, float partialTick, LevelReader level, float size,
                                               CallbackInfo ci) {
        if (DuctTravelEntity.isHiddenInTransit(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void zps$noHitboxInTheDucts(PoseStack poseStack, VertexConsumer buffer, Entity entity,
                                               float partialTick, float red, float green, float blue,
                                               CallbackInfo ci) {
        if (DuctTravelEntity.isHiddenInTransit(entity)) {
            ci.cancel();
        }
    }
}
