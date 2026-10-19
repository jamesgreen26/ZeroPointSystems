package g_mungus.zps.gametest;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.compat.GridPhysicsActor;
import g_mungus.zps.compat.SableGridPhysics;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

/**
 * The Sable side of {@code GridPhysicsGameTests}, kept apart so that class loads without Sable.
 * <p>
 * Sable clears what was queued on a sublevel at the start of every substep and applies what its actors queue
 * during it, so a push has to be made from inside the substep, as an actor's is. A pre-physics listener stands
 * in for one, handed the same {@link SableGridPhysics} an actor on the rig's sublevel would be.
 */
final class SableGridPhysicsProbe {
    private SableGridPhysicsProbe() {}

    private static volatile ServerSubLevel target;
    private static volatile Consumer<GridPhysics> action;
    private static boolean listening;

    /** Whether the mixin has made every {@link GridPhysicsActor} one Sable collects from a plot. */
    static boolean actorIsSableActor() {
        return BlockEntitySubLevelActor.class.isAssignableFrom(GridPhysicsActor.class);
    }

    /** Runs {@code onSubstep} in every physics substep of the rig's sublevel until {@link #stop}. */
    static void everySubstep(SableBeamRig rig, Consumer<GridPhysics> onSubstep) {
        if (!listening) {
            listening = true;
            SableEventPlatform.INSTANCE.onPhysicsTick((system, timeStep) -> {
                ServerSubLevel subLevel = target;
                Consumer<GridPhysics> run = action;
                if (subLevel != null && run != null && !subLevel.isRemoved()) {
                    run.accept(new SableGridPhysics(subLevel, system.getPhysicsHandle(subLevel), timeStep));
                }
            });
        }
        target = rig.subLevel();
        action = onSubstep;
    }

    static void stop() {
        action = null;
        target = null;
    }

    /** The rig's sublevel as a {@link GridPhysics}, for reading from the server tick. */
    static GridPhysics physicsOf(SableBeamRig rig) {
        return new SableGridPhysics(rig.subLevel(), RigidBodyHandle.of(rig.subLevel()), 0.05);
    }

    static void setPhysicsPaused(ServerLevel level, boolean paused) {
        SubLevelContainer.getContainer(level).physicsSystem().setPaused(paused);
    }
}
