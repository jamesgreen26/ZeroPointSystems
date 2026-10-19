package g_mungus.zps.compat;

/**
 * A block entity that pushes the moving grid it sits on, a Valkyrien Skies ship or a Sable sublevel, from inside
 * that grid's physics step.
 * <p>
 * Nothing here names either mod. Each one gets its own interface mixin that makes this one extend the mod's
 * block entity physics hook ({@code mixin.sable.SubLevelActorExtension}, {@code mixin.vs.PhysicsListenerExtension})
 * and forwards it to {@link #onGridPhysicsTick}; {@code ZPSMixinCanceller} drops the mixin for a mod that is not
 * installed. With neither installed the method is simply never called.
 */
public interface GridPhysicsActor {

    /**
     * Once per physics step while this block entity is on a moving grid; never for one in the world.
     * <p>
     * Valkyrien Skies runs its physics on a thread of its own, so this may be called off the server thread.
     * Read only what the server tick has already published (an immutable snapshot in a volatile field), and
     * never touch the level from here.
     */
    void onGridPhysicsTick(GridPhysics physics);
}
