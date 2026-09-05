package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws one reactor: an instance per wall-touching cell, all sharing the one model. The shaders do
 * the animation from the frame clock; the only thing that changes here is the heat, eased toward
 * the server's figure every tick and written to the instances when it has moved enough to show.
 *
 * <p>Built complete in the constructor: Flywheel flushes new instances the same frame, before
 * the first tick, and recreates every visual from scratch when the render origin moves.
 */
public final class ReactorEffectVisual extends AbstractVisual implements EffectVisual<ReactorEffect>, SimpleTickableVisual {

    /** Fraction of the remaining gap closed each tick. */
    private static final float SMOOTHING = 0.15f;
    /** Heat changes smaller than this are not worth rewriting every cell. */
    private static final float WRITE_EPSILON = 1f / 256f;
    /** Below this the glow is invisible; the cells are hidden so they cost nothing. */
    private static final float VISIBLE_HEAT = 0.02f;

    private final ClientReactor reactor;
    private final ReactorCellInstance[] cells;
    private float writtenHeat;
    private boolean visible;

    public ReactorEffectVisual(VisualizationContext ctx, Level level, ClientReactor reactor, float partialTick) {
        super(ctx, level, partialTick);
        this.reactor = reactor;

        Vec3i origin = renderOrigin();
        long[] positions = reactor.cells();
        int[] faces = reactor.faces();
        float heat = reactor.displayHeat();
        AABB bounds = reactor.shape().bounds();
        float seed = seed(reactor.id());
        Instancer<ReactorCellInstance> instancer = instancerProvider().instancer(ReactorFlywheel.INSTANCE_TYPE, ReactorFlywheel.MODEL);

        cells = new ReactorCellInstance[positions.length];
        for (int i = 0; i < positions.length; i++) {
            long pos = positions[i];
            ReactorCellInstance cell = instancer.createInstance();
            cell.x = BlockPos.getX(pos) - origin.getX();
            cell.y = BlockPos.getY(pos) - origin.getY();
            cell.z = BlockPos.getZ(pos) - origin.getZ();
            cell.minX = (float) (bounds.minX - origin.getX());
            cell.minY = (float) (bounds.minY - origin.getY());
            cell.minZ = (float) (bounds.minZ - origin.getZ());
            cell.maxX = (float) (bounds.maxX - origin.getX());
            cell.maxY = (float) (bounds.maxY - origin.getY());
            cell.maxZ = (float) (bounds.maxZ - origin.getZ());
            cell.faces = faces[i];
            cell.intensity = heat;
            cell.seed = seed;
            cells[i] = cell;
        }
        writtenHeat = heat;
        visible = true;
        setVisible(heat >= VISIBLE_HEAT);
    }

    /** A stable 0..1 phase from the reactor id, so two reactors side by side do not match. */
    private static float seed(int id) {
        long h = (id + 1L) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 0xFFFF) / 65536f;
    }

    @Override
    public void tick(TickableVisual.Context context) {
        float target = reactor.targetHeat();
        float display = reactor.displayHeat();
        display += (target - display) * SMOOTHING;
        if (Math.abs(target - display) < WRITE_EPSILON / 4) {
            display = target;
        }
        reactor.setDisplayHeat(display);

        setVisible(display >= VISIBLE_HEAT);
        if (Math.abs(display - writtenHeat) > WRITE_EPSILON) {
            writtenHeat = display;
            for (ReactorCellInstance cell : cells) {
                cell.setIntensity(display);
            }
        }
    }

    private void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        for (ReactorCellInstance cell : cells) {
            cell.setVisible(visible);
        }
    }

    @Override
    protected void _delete() {
        for (ReactorCellInstance cell : cells) {
            cell.delete();
        }
    }
}
