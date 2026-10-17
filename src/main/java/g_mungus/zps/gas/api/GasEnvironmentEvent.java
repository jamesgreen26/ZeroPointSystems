package g_mungus.zps.gas.api;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Fired on the game bus once per server tick for every living entity, with its {@link GasEnvironment}
 * after the atmosphere and any queued modifiers have had their say. For sources that can tell from
 * the entity itself that they apply, such as what it is riding or wearing; a source that finds the
 * entities it affects from its own tick queues a modifier with {@link GasEnvironments#queue} instead.
 * Listener priority orders the changes.
 */
public class GasEnvironmentEvent extends Event {
    private final LivingEntity entity;
    private final GasEnvironment environment;

    public GasEnvironmentEvent(LivingEntity entity, GasEnvironment environment) {
        this.entity = entity;
        this.environment = environment;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public GasEnvironment getEnvironment() {
        return environment;
    }
}
