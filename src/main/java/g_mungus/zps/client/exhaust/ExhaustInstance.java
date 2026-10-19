package g_mungus.zps.client.exhaust;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;

/**
 * One exhaust particle's cube. It holds nothing but which slot of the batch ring it draws: the
 * shader works the particle out from that slot's batch record, so an instance is written once,
 * when it is made, and never again.
 */
public class ExhaustInstance extends AbstractInstance {

    public int slot;

    public ExhaustInstance(InstanceType<?> type, InstanceHandle handle) {
        super(type, handle);
    }
}
