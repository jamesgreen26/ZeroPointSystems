package g_mungus.zps.gas.api;

import g_mungus.zps.gas.AtmosphereBreather;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Queues changes to an entity's next {@link GasEnvironment}. For a source that finds the entities it
 * affects from its own tick, such as a reactor sweeping its cavity: queue every tick the entity is
 * affected. A modifier is applied to the environment built in the entity's next tick, and dropped if
 * that tick does not come within one game tick of queueing it.
 *
 * <p>Queued modifiers run in the order queued, before any {@link GasEnvironmentEvent} listener.
 * Server side.
 */
public final class GasEnvironments {

    private GasEnvironments() {
    }

    public static void queue(LivingEntity entity, GasEnvironmentModifier modifier) {
        AtmosphereBreather breather = (AtmosphereBreather) entity;
        long now = entity.level().getGameTime();
        if (now - breather.zps$getQueuedTick() > 1) {
            breather.zps$getQueuedModifiers().clear();
        }
        breather.zps$getQueuedModifiers().add(modifier);
        breather.zps$setQueuedTick(now);
    }

    /** Apply and clear what is queued for {@code environment}'s entity, unless it has gone stale. */
    public static void applyQueued(GasEnvironment environment) {
        AtmosphereBreather breather = (AtmosphereBreather) environment.entity();
        List<GasEnvironmentModifier> queued = breather.zps$getQueuedModifiers();
        if (queued.isEmpty()) {
            return;
        }
        long since = environment.entity().level().getGameTime() - breather.zps$getQueuedTick();
        if (since >= 0 && since <= 1) {
            for (GasEnvironmentModifier modifier : queued) {
                modifier.modify(environment);
            }
        }
        queued.clear();
    }
}
