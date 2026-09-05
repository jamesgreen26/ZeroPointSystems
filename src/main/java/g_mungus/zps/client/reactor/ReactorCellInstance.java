package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;

/**
 * One interior cell of a reactor that touches a wall. The shaders need where it is, which of its
 * six faces are against a wall, and how hot the reactor is.
 *
 * <p>Every field must be set before the first flush, which happens the same frame the visual is
 * created, so the visual assigns all of them in its constructor.
 */
public final class ReactorCellInstance extends AbstractInstance {

    /** Cell minimum corner, render space. */
    public float x, y, z;
    /** The whole reactor's bounding box, render space. The same on every cell of a reactor. */
    public float minX, minY, minZ, maxX, maxY, maxZ;
    /** Reactor heat: chamber temperature over ignition temperature. */
    public float intensity;
    /** Per-reactor noise phase. The same on every cell, so the field is continuous across them. */
    public float seed;
    /** Bit per {@link net.minecraft.core.Direction} ordinal, set where that face is against a wall. */
    public int faces;
    /** Cavity depth behind each face in blocks, a byte per direction ordinal: 0..3 low, 4..5 high. */
    public int depthsLow, depthsHigh;

    public ReactorCellInstance(InstanceType<?> type, InstanceHandle handle) {
        super(type, handle);
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        setChanged();
    }
}
