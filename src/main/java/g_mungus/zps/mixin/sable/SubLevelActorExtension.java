package g_mungus.zps.mixin.sable;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import g_mungus.zps.compat.GridPhysicsActor;
import g_mungus.zps.compat.SableGridPhysics;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes every {@link GridPhysicsActor} a Sable block entity actor. Sable collects its actors from a plot by
 * {@code instanceof}, so one on a sublevel is found with no registration of its own.
 */
@Mixin(value = GridPhysicsActor.class, remap = false)
public interface SubLevelActorExtension extends BlockEntitySubLevelActor {

    @Override
    default void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ((GridPhysicsActor) this).onGridPhysicsTick(new SableGridPhysics(subLevel, handle, timeStep));
    }
}
