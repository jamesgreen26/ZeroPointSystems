package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one reactor: an instance per wall-touching cell, all sharing the one model. The shaders do
 * the animation from the frame clock; the only thing that changes here is the heat, eased toward
 * the server's figure every tick and written to the instances when it has moved enough to show.
 *
 * <p>Built complete in the constructor: Flywheel flushes new instances the same frame, before
 * the first tick, and recreates every visual from scratch when the render origin moves.
 */
public final class ReactorEffectVisual extends AbstractVisual implements EffectVisual<ReactorEffect>, SimpleTickableVisual {

    /**
     * Fraction of the remaining gap closed each tick, and the most the heat may move in one tick.
     * Together they turn the server's ten-tick steps, and a sputtering reactor's swings, into a
     * slow glide: a jump of one full ignition takes about a second and a half to show.
     */
    private static final float SMOOTHING = 0.08f;
    private static final float MAX_STEP = 0.035f;
    /** Heat changes smaller than this are not worth rewriting every cell. */
    private static final float WRITE_EPSILON = 1f / 256f;
    /** Below this the glow is invisible; the cells are hidden so they cost nothing. */
    private static final float VISIBLE_HEAT = 0.02f;
    /** Every face bit set: a wall coat is all surface, with no side of it to collapse. */
    private static final int ALL_FACES = (1 << 6) - 1;

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
        int[] depthsLow = reactor.depthsLow();
        int[] depthsHigh = reactor.depthsHigh();
        float heat = reactor.displayHeat();
        AABB bounds = reactor.shape().bounds();
        float seed = seed(reactor.id());
        Instancer<ReactorCellInstance> instancer = instancerProvider().instancer(ReactorFlywheel.INSTANCE_TYPE, ReactorFlywheel.MODEL);

        List<ReactorCellInstance> instances = new ArrayList<>(positions.length);
        BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
        for (int i = 0; i < positions.length; i++) {
            long pos = positions[i];

            // A wall block whose model reaches into this cell has moved part of the cavity's surface
            // off the boundary the shared mesh coats. That side is handed over whole: the coat built
            // for it covers the protrusion and the flat face around it, in quads that meet along the
            // footprint's edges, and the cell's own face there is collapsed so nothing is coated
            // twice. Two coats over the same ground would sort out in the depth test as a seam.
            int own = faces[i];
            for (Direction side : Direction.values()) {
                if ((faces[i] & (1 << side.ordinal())) == 0) {
                    continue;
                }
                wall.set(BlockPos.getX(pos) + side.getStepX(),
                        BlockPos.getY(pos) + side.getStepY(),
                        BlockPos.getZ(pos) + side.getStepZ());
                Model coat = WallCoats.of(level.getBlockState(wall), side.getOpposite());
                if (coat == null) {
                    continue;
                }
                own &= ~(1 << side.ordinal());
                ReactorCellInstance instance = instancerProvider()
                        .instancer(ReactorFlywheel.INSTANCE_TYPE, coat).createInstance();
                place(instance, pos, origin, bounds, ALL_FACES, depthsLow[i], depthsHigh[i], heat, seed);
                instances.add(instance);
            }

            // Nothing left for the shared mesh when every wall of the cell was handed over.
            if (own != 0) {
                ReactorCellInstance cell = instancer.createInstance();
                place(cell, pos, origin, bounds, own, depthsLow[i], depthsHigh[i], heat, seed);
                instances.add(cell);
            }
        }
        cells = instances.toArray(new ReactorCellInstance[0]);
        writtenHeat = heat;
        visible = true;
        setVisible(heat >= VISIBLE_HEAT);
    }

    /**
     * Everything an instance needs but the mesh: where the cell is, the reactor around it, which of
     * the mesh's faces to keep, and the heat. The wall coats share all of it with their cell, which
     * is what keeps them seamless with it — the fragment shader works from position alone.
     */
    private static void place(ReactorCellInstance instance, long pos, Vec3i origin, AABB bounds,
                              int faces, int depthsLow, int depthsHigh, float heat, float seed) {
        instance.x = BlockPos.getX(pos) - origin.getX();
        instance.y = BlockPos.getY(pos) - origin.getY();
        instance.z = BlockPos.getZ(pos) - origin.getZ();
        instance.minX = (float) (bounds.minX - origin.getX());
        instance.minY = (float) (bounds.minY - origin.getY());
        instance.minZ = (float) (bounds.minZ - origin.getZ());
        instance.maxX = (float) (bounds.maxX - origin.getX());
        instance.maxY = (float) (bounds.maxY - origin.getY());
        instance.maxZ = (float) (bounds.maxZ - origin.getZ());
        instance.faces = faces;
        instance.depthsLow = depthsLow;
        instance.depthsHigh = depthsHigh;
        instance.intensity = heat;
        instance.seed = seed;
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
        float step = (target - display) * SMOOTHING;
        display += Math.max(-MAX_STEP, Math.min(MAX_STEP, step));
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
