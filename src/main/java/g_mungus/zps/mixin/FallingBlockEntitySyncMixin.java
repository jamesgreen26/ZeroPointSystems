package g_mungus.zps.mixin;

import g_mungus.zps.entity.EasedSync;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes falling blocks {@link EasedSync}: the client keeps moving them itself, and makes up what the server says
 * it got wrong a little each tick. {@link EntityMixin} is where the server's word comes in.
 */
@Mixin(FallingBlockEntity.class)
public class FallingBlockEntitySyncMixin implements EasedSync {

    /** How far the block still has to be moved to be where the server has it. */
    @Unique
    private Vec3 zps$owed = Vec3.ZERO;

    @Unique
    private int zps$owedTicks;

    @Override
    public boolean zps$easeToward(double x, double y, double z) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!self.level().isClientSide) {
            return false;
        }
        Vec3 error = new Vec3(x, y, z).subtract(self.position());
        // Gravity comes off before the block moves, so this is the step the coming tick takes.
        Vec3 step = self.getDeltaMovement().subtract(0.0, self.getGravity(), 0.0);
        Vec3 wrong = EasedSync.residual(error, step);
        if (wrong.lengthSqr() > SNAP_DISTANCE * SNAP_DISTANCE) {
            zps$owedTicks = 0;
            return false;
        }
        // Each report replaces the last: it was measured from where the block is now.
        zps$owed = wrong;
        zps$owedTicks = TICKS;
        return true;
    }

    /**
     * At the head of the tick the old position has just been taken, so the correction is drawn as part of this
     * tick's movement.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void zps$makeUpWhatIsOwed(CallbackInfo ci) {
        if (zps$owedTicks <= 0) {
            return;
        }
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        Vec3 part = zps$owed.scale(1.0 / zps$owedTicks);
        self.setPos(self.position().add(part));
        zps$owed = zps$owed.subtract(part);
        zps$owedTicks--;
    }
}
