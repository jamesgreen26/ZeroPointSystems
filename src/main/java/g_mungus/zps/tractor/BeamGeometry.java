package g_mungus.zps.tractor;

import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The volume a Beam Collector panel sweeps: a {@code width x width} prism running {@code range} blocks out from the
 * panel's mouth, split into one column per panel block.
 * <p>
 * Everything here is in the panel's own grid. On a sublevel that grid is not the world, so positions of entities
 * have to come through {@link #toLocal} first and velocities have to leave through {@link #toWorldVector}.
 *
 * @param controller the panel's minimum corner
 * @param facing     the way the mouth points, which is the way the beam extends
 * @param width      panel blocks per side
 * @param range      beam length in blocks
 */
public record BeamGeometry(BlockPos controller, Direction facing, int width, int range) {

    /** How far in front of the mouth things are swallowed. Deeper than anything moves in a tick. */
    public static final double COLLECTION_DEPTH = 1.0;

    /** The first of the two positive axes spanning the panel; matches {@code ConnectivityHandler}'s layout. */
    public Direction.Axis axisA() {
        return facing.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    public Direction.Axis axisB() {
        return facing.getAxis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    public int columns() {
        return width * width;
    }

    /** The panel block heading column {@code index}. */
    public BlockPos columnBase(int index) {
        return controller.relative(axisA(), index / width).relative(axisB(), index % width);
    }

    /** The block {@code distance} (1-based) out along column {@code index}. */
    public BlockPos columnBlock(int index, int distance) {
        return columnBase(index).relative(facing, distance);
    }

    /** Coordinate of the mouth plane along the facing axis. */
    private double mouthPlane() {
        int base = controller.get(facing.getAxis());
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? base + 1 : base;
    }

    /** Distance out from the mouth plane, positive in front of the panel. */
    public double axialDistance(Vec3 local) {
        return (local.get(facing.getAxis()) - mouthPlane()) * facing.getAxisDirection().getStep();
    }

    /** Column a local position falls in, or -1 when it is outside the panel's footprint. */
    public int columnAt(Vec3 local) {
        double a = local.get(axisA()) - controller.get(axisA());
        double b = local.get(axisB()) - controller.get(axisB());
        if (a < 0 || b < 0 || a >= width || b >= width) {
            return -1;
        }
        return (int) a * width + (int) b;
    }

    /**
     * The column nearest a local position that is beside the panel's footprint rather than in it, by no more than
     * {@code slack} blocks; -1 when it is further off than that.
     */
    public int nearestColumn(Vec3 local, double slack) {
        double a = local.get(axisA()) - controller.get(axisA());
        double b = local.get(axisB()) - controller.get(axisB());
        if (a < -slack || b < -slack || a >= width + slack || b >= width + slack) {
            return -1;
        }
        return Mth.clamp((int) Math.floor(a), 0, width - 1) * width + Mth.clamp((int) Math.floor(b), 0, width - 1);
    }

    /** {@code local} moved sideways onto the centre line of column {@code index}, keeping its distance out. */
    public Vec3 onColumnAxis(int index, Vec3 local) {
        Vec3 centre = Vec3.atCenterOf(columnBase(index));
        return local.with(axisA(), centre.get(axisA())).with(axisB(), centre.get(axisB()));
    }

    /** {@code local} moved sideways onto the centre line of the whole beam. */
    public Vec3 onBeamAxis(Vec3 local) {
        double half = width / 2.0;
        return local.with(axisA(), controller.get(axisA()) + half).with(axisB(), controller.get(axisB()) + half);
    }

    public Vec3 mouthCentre() {
        return onBeamAxis(Vec3.ZERO).with(facing.getAxis(), mouthPlane());
    }

    /** Unit vector from the far end of the beam toward the mouth. */
    public Vec3 pullDirection() {
        return Vec3.atLowerCornerOf(facing.getOpposite().getNormal());
    }

    /** The whole beam volume, in the panel's grid. */
    public AABB localBounds() {
        AABB panel = new AABB(
                Vec3.atLowerCornerOf(controller),
                Vec3.atLowerCornerOf(columnBase(columns() - 1).offset(1, 1, 1)));
        Vec3 reach = Vec3.atLowerCornerOf(facing.getNormal()).scale(range);
        AABB swept = panel.expandTowards(reach);
        // Drop the panel's own block layer so the volume starts at the mouth plane.
        Vec3 trim = Vec3.atLowerCornerOf(facing.getNormal());
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? new AABB(swept.minX + trim.x, swept.minY + trim.y, swept.minZ + trim.z,
                        swept.maxX, swept.maxY, swept.maxZ)
                : new AABB(swept.minX, swept.minY, swept.minZ,
                        swept.maxX + trim.x, swept.maxY + trim.y, swept.maxZ + trim.z);
    }

    /** A world-space box that contains the whole beam, however the panel's grid is posed. */
    public AABB worldBounds(Level level) {
        AABB local = localBounds();
        if (!onMovingGrid()) {
            return local;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 world = Compat.toWorldPos(level, controller, new Vec3(
                    (corner & 1) == 0 ? local.minX : local.maxX,
                    (corner & 2) == 0 ? local.minY : local.maxY,
                    (corner & 4) == 0 ? local.minZ : local.maxZ));
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            maxZ = Math.max(maxZ, world.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Whether a grid mod is present at all; without one every transform below is the identity. */
    private static boolean onMovingGrid() {
        return Compat.isVSLoaded() || Compat.isSableLoaded();
    }

    /** A world position expressed in the panel's grid. */
    public Vec3 toLocal(Level level, Vec3 world) {
        return onMovingGrid() ? Compat.toLocalSpaceOf(level, controller, world) : world;
    }

    /** A position in the panel's grid expressed in the world. */
    public Vec3 toWorld(Level level, Vec3 local) {
        return onMovingGrid() ? Compat.toWorldPos(level, controller, local) : local;
    }

    /** A direction or velocity in the panel's grid, turned to match the world. */
    public Vec3 toWorldVector(Level level, Vec3 localOrigin, Vec3 localVector) {
        if (!onMovingGrid()) {
            return localVector;
        }
        return Compat.toWorldPos(level, controller, localOrigin.add(localVector))
                .subtract(Compat.toWorldPos(level, controller, localOrigin));
    }

    /** The inverse of {@link #toWorldVector}. */
    public Vec3 toLocalVector(Level level, Vec3 worldOrigin, Vec3 worldVector) {
        if (!onMovingGrid()) {
            return worldVector;
        }
        return Compat.toLocalSpaceOf(level, controller, worldOrigin.add(worldVector))
                .subtract(Compat.toLocalSpaceOf(level, controller, worldOrigin));
    }
}
